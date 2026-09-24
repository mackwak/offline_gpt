import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { onCall, onRequest, HttpsError } from 'firebase-functions/v2/https';
import * as logger from 'firebase-functions/logger';
import {
    generateRegistrationOptions,
    verifyRegistrationResponse,
    generateAuthenticationOptions,
    verifyAuthenticationResponse,
} from '@simplewebauthn/server';

initializeApp();
const db = getFirestore();
const auth = getAuth();

const rpName = 'OfflineGPT App';
const rpID = 'helloworld-6tfetltbzq-uc.a.run.app';
const assetLinks = [
    {
        relation: [
            'delegate_permission/common.handle_all_urls',
            'delegate_permission/common.get_login_creds',
        ],
        target: {
            namespace: 'android_app',
            package_name: 'compass.assistant.dev',
            sha256_cert_fingerprints: [
                '28:D2:E0:12:4D:02:52:0F:19:68:7C:C0:A4:55:0A:85:53:A0:C4:FE:48:62:51:45:F8:96:D5:27:51:34:4B:75',
                '80:AF:06:F0:26:E9:14:B6:D1:BD:27:DE:D8:EB:CA:93:2B:DD:F5:07:20:3F:97:2E:46:84:48:89:71:80:A2:58',
            ],
        },
    },
];

function toVerificationHttpsError(error, operation) {
    const rawMessage = error instanceof Error ? error.message : String(error);
    const message = rawMessage.toLowerCase();

    logger.error(`${operation} verification error`, { rawMessage, rpID });

    if (message.includes('origin')) {
        return new HttpsError(
            'failed-precondition',
            'Passkey origin mismatch. Check assetlinks.json, app package name, signing certificate, and that the installed app matches the deployed rpID domain.',
            { rawMessage, rpID }
        );
    }

    if (message.includes('rp id') || message.includes('rpid')) {
        return new HttpsError(
            'failed-precondition',
            'Passkey RP ID mismatch. Ensure the app is linked to the deployed rpID domain and try again after reinstalling the app.',
            { rawMessage, rpID }
        );
    }

    if (message.includes('challenge')) {
        return new HttpsError(
            'failed-precondition',
            'Passkey challenge expired or did not match. Start the passkey flow again.',
            { rawMessage }
        );
    }

    if (message.includes('counter')) {
        return new HttpsError(
            'failed-precondition',
            'Passkey counter validation failed. This credential may be stale or duplicated.',
            { rawMessage }
        );
    }

    return new HttpsError('internal', rawMessage, { rawMessage, rpID });
}

// Supported client origins
const expectedOrigin = [
    'android:apk-key-hash:KNLgEk0CUg8ZaHzApFUKhVOgxP5IYlFF-JbVJ1E0S3U', // dev/qa/prod debug signing cert
    'android:apk-key-hash:gK8G8CbpFLbRvSfe2OvKkyvd9QcgP5cuRoRIiXGAolg', // release signing cert
    'https://helloworld-6tfetltbzq-uc.a.run.app'
];

// 1. Passkey Registration Request Options
export const requestRegistration = onCall({ invoker: "public", enforceAppCheck: false }, async (request) => {
    const { email } = request.data || {};
    if (!email) throw new HttpsError('invalid-argument', 'Email is required');

    const userPasskeys = await db.collection('users').doc(email).collection('passkeys').get();
    const excludeCredentials = userPasskeys.docs.map(doc => ({
        id: doc.id,
        type: 'public-key',
    }));

    const options = await generateRegistrationOptions({
        rpName,
        rpID,
        userID: Buffer.from(email),
        userName: email,
        attestationType: 'none',
        excludeCredentials,
        authenticatorSelection: {
            residentKey: 'preferred',
            userVerification: 'preferred',
            authenticatorAttachment: 'platform',
        },
    });

    await db.collection('users').doc(email).set({
        currentChallenge: options.challenge
    }, { merge: true });

    return options;
});

// 2. Passkey Registration Verification
export const verifyRegistration = onCall({ invoker: "public", enforceAppCheck: false }, async (request) => {
    const { email, registrationResponse } = request.data || {};
    if (!email || !registrationResponse) {
        throw new HttpsError('invalid-argument', 'Email and registrationResponse are required');
    }

    const body = typeof registrationResponse === 'string' 
        ? JSON.parse(registrationResponse) 
        : registrationResponse;

    const userDoc = await db.collection('users').doc(email).get();
    if (!userDoc.exists) throw new HttpsError('not-found', 'User not found');

    const expectedChallenge = userDoc.data().currentChallenge;

    let verification;
    try {
        verification = await verifyRegistrationResponse({
            response: body,
            expectedChallenge,
            expectedOrigin,
            expectedRPID: rpID,
        });
    } catch (error) {
        throw toVerificationHttpsError(error, 'Registration');
    }

    const { verified, registrationInfo } = verification;

    if (verified && registrationInfo) {
        // Support both @simplewebauthn/server v10+ (nested under .credential) and legacy v9 structure
        const credential = registrationInfo.credential;
        const credIdStr = credential?.id || Buffer.from(registrationInfo.credentialID).toString('base64url');
        const pubKeyStr = credential?.publicKey 
            ? Buffer.from(credential.publicKey).toString('base64url') 
            : Buffer.from(registrationInfo.credentialPublicKey).toString('base64url');
        const counter = credential?.counter ?? registrationInfo.counter ?? 0;

        await db.collection('users').doc(email).collection('passkeys').doc(credIdStr).set({
            publicKey: pubKeyStr,
            credentialID: credIdStr,
            counter,
            transports: body.response?.transports || [],
        });

        return { success: true };
    }

    throw new HttpsError('unauthenticated', 'Verification failed');
});

// 3. Passkey Authentication Request Options
export const requestAuthentication = onCall({ invoker: "public", enforceAppCheck: false }, async (request) => {
    const { email } = request.data || {};
    if (!email) throw new HttpsError('invalid-argument', 'Email is required');

    const userPasskeys = await db.collection('users').doc(email).collection('passkeys').get();
    if (userPasskeys.empty) throw new HttpsError('not-found', 'No passkeys found for this user');

    const allowCredentials = userPasskeys.docs.map(doc => ({
        id: doc.id,
        type: 'public-key',
        transports: doc.data().transports,
    }));

    const options = await generateAuthenticationOptions({
        rpID,
        allowCredentials,
        userVerification: 'preferred',
    });

    await db.collection('users').doc(email).set({
        currentChallenge: options.challenge
    }, { merge: true });

    return options;
});

// 4. Passkey Authentication Verification
export const verifyAuthentication = onCall({ invoker: "public", enforceAppCheck: false }, async (request) => {
    const { email, authResponse } = request.data || {};
    if (!email || !authResponse) {
        throw new HttpsError('invalid-argument', 'Email and authResponse are required');
    }

    const body = typeof authResponse === 'string' ? JSON.parse(authResponse) : authResponse;

    const userDoc = await db.collection('users').doc(email).get();
    if (!userDoc.exists) throw new HttpsError('not-found', 'User not found');

    const expectedChallenge = userDoc.data().currentChallenge;

    const passkeyDoc = await db.collection('users').doc(email).collection('passkeys').doc(body.id).get();
    if (!passkeyDoc.exists) throw new HttpsError('not-found', 'Passkey not found');

    const passkey = passkeyDoc.data();

    let verification;
    try {
        verification = await verifyAuthenticationResponse({
            response: body,
            expectedChallenge,
            expectedOrigin,
            expectedRPID: rpID,
            credential: {
                id: passkey.credentialID,
                publicKey: Buffer.from(passkey.publicKey, 'base64url'),
                counter: passkey.counter,
                transports: passkey.transports,
            },
        });
    } catch (error) {
        throw toVerificationHttpsError(error, 'Authentication');
    }

    const { verified, authenticationInfo } = verification;

    if (verified && authenticationInfo) {
        await passkeyDoc.ref.update({
            counter: authenticationInfo.newCounter
        });

        let uid;
        try {
            const userRecord = await auth.getUserByEmail(email);
            uid = userRecord.uid;
        } catch (e) {
            const userRecord = await auth.createUser({
                email,
                emailVerified: true,
                displayName: email.split('@')[0]
            });
            uid = userRecord.uid;
        }

        const token = await auth.createCustomToken(uid);

        await db.collection('users').doc(email).update({
            lastLogin: FieldValue.serverTimestamp(),
            lastLoginMethod: 'passkey'
        });

        return { token };
    }

    throw new HttpsError('unauthenticated', 'Authentication failed');
});

// 5. Test Callable Function
export const helloWorldOnCall = onCall({ invoker: "public", enforceAppCheck: false }, (request) => {
    logger.info("Hello onCall logs!", { structuredData: true });
    const text = "Hello from Firebase Callable Function!";
    return { response: text, message: text };
});

// 6. Test HTTP Endpoint
export const helloWorld = onRequest({ invoker: "public", enforceAppCheck: false }, (request, response) => {
    if (request.path === '/.well-known/assetlinks.json') {
        logger.info('Serving assetlinks.json from Cloud Run domain', { structuredData: true });
        response.set('Content-Type', 'application/json');
        response.set('Cache-Control', 'public, max-age=3600');
        response.status(200).send(assetLinks);
        return;
    }

    logger.info("Hello logs!", { structuredData: true });
    response.send("Hello from Firebase!");
});
