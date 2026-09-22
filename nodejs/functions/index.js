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
const rpID = 'offlinegpt.example.com';
const callableOptions = {
    region: 'us-central1',
    invoker: 'public',
    enforceAppCheck: false,
};

function getRequestEmail(request) {
    const email = String(request.data?.email || '').trim().toLowerCase();
    if (!email) {
        throw new HttpsError('invalid-argument', 'Email is required');
    }
    // Basic server-side validation to reject malformed requests early.
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailPattern.test(email)) {
        throw new HttpsError('invalid-argument', 'Invalid email format');
    }
    return email;
}

// Supported client origins
const expectedOrigin = [
    'android:apk-key-hash:KNLgEk0CUgeZaHzApFUKhVOgxP5IYlFF-JbVJ1E0S3U', // Base64URL SHA-256 fingerprint
    'https://offlinegpt.example.com',                                  // Production domain
    'http://localhost:5001',                                           // Local Firebase Emulator
    'http://10.0.2.2:5001'                                            // Android Emulator loopback
];

// 1. Passkey Registration Request Options
export const requestRegistration = onCall(callableOptions, async (request) => {
	
	console.log('aaa');
	
    const email = getRequestEmail(request);

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
export const verifyRegistration = onCall(callableOptions, async (request) => {
	
	console.log('bbb');
		
    const email = getRequestEmail(request);
    const { registrationResponse } = request.data || {};
    if (!registrationResponse) {
        throw new HttpsError('invalid-argument', 'registrationResponse is required');
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
        logger.error('Registration Verification Error:', error);
        throw new HttpsError('internal', error.message);
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
export const requestAuthentication = onCall(callableOptions, async (request) => {
	
	console.log('ccc');
	
    const email = getRequestEmail(request);

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
export const verifyAuthentication = onCall(callableOptions, async (request) => {
	
	console.log('ddd');
	
    const email = getRequestEmail(request);
    const { authResponse } = request.data || {};
    if (!authResponse) {
        throw new HttpsError('invalid-argument', 'authResponse is required');
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
        logger.error('Authentication Verification Error:', error);
        throw new HttpsError('internal', error.message);
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
export const helloWorldOnCall = onCall(callableOptions, (request) => {
	
	console.log('eee');
	
    logger.info("Hello onCall logs!", { structuredData: true });
    const text = "Hello from Firebase Callable Function!";
    return { response: text, message: text };
});

// 6. Test HTTP Endpoint
export const helloWorld = onRequest({ invoker: "public", region: 'us-central1' }, (request, response) => {
    logger.info("Hello logs!", { structuredData: true });
    response.send("Hello from Firebase!");
});
