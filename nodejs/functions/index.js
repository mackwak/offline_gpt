import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import * as logger from 'firebase-functions/logger';
import {
    generateRegistrationOptions,
    verifyRegistrationResponse,
    generateAuthenticationOptions,
    verifyAuthenticationResponse,
} from '@simplewebauthn/server';

initializeApp();
const db = getFirestore('webauthn');
const auth = getAuth();

// 설정
const rpName = 'OfflineGPT';
const rpID = 'offlinegpt-dev.web.app';

// 안드로이드 디버그 빌드 서명 해시가 적용된 오리진
const expectedOrigin = 'android:apk-key-hash:KNLgEk0CUg8ZaHzApFUKhVOgxP5IYlFF-JbVJ1E0S3U';

const getErrorMessage = (error) => {
  if (error instanceof HttpsError) return error.message;
  if (error instanceof Error) return error.message;
  return '알 수 없는 서버 오류가 발생했습니다.';
};

const rethrowHttpsError = (error) => {
  if (error instanceof HttpsError) {
    throw error;
  }
  throw new HttpsError('internal', getErrorMessage(error));
};

const normalizeEmail = (value) => {
  if (typeof value !== 'string') return null;
  const normalized = value.trim().toLowerCase();
  return normalized.length > 0 ? normalized : null;
};

// Safely normalize or extract base64url string credential ID
const safeBase64UrlString = (val) => {
  if (!val) return '';
  if (typeof val === 'string') return val.trim();
  if (Buffer.isBuffer(val) || val instanceof Uint8Array) {
    return Buffer.from(val).toString('base64url');
  }
  return String(val);
};

// Convert credential ID (handling single or legacy double-encoded base64url) to Buffer
const toCredentialIdBuffer = (val) => {
  if (!val) return Buffer.alloc(0);
  let str = safeBase64UrlString(val);

  try {
    const decodedAscii = Buffer.from(str, 'base64url').toString('utf-8');
    if (/^[A-Za-z0-9_-]+$/.test(decodedAscii) && decodedAscii.length >= 8 && decodedAscii.length <= 200) {
      str = decodedAscii;
    }
  } catch (e) {}

  return Buffer.from(str, 'base64url');
};

// Convert credential public key (handling single or legacy double-encoded base64url) to Buffer
const toPublicKeyBuffer = (val) => {
  if (!val) return Buffer.alloc(0);
  let str = safeBase64UrlString(val);

  try {
    const decodedAscii = Buffer.from(str, 'base64url').toString('utf-8');
    if (/^[A-Za-z0-9_-]+$/.test(decodedAscii) && decodedAscii.length >= 20 && decodedAscii.length <= 500) {
      str = decodedAscii;
    }
  } catch (e) {}

  return Buffer.from(str, 'base64url');
};

// Check if two credential ID values match (handling raw base64url and double-encoded base64url)
const credentialIdsMatch = (id1, id2) => {
  if (!id1 || !id2) return false;
  const str1 = safeBase64UrlString(id1);
  const str2 = safeBase64UrlString(id2);
  if (str1 === str2) return true;

  try {
    const ascii1 = Buffer.from(str1, 'base64url').toString('utf-8');
    if (ascii1 === str2) return true;
  } catch (e) {}

  try {
    const ascii2 = Buffer.from(str2, 'base64url').toString('utf-8');
    if (ascii2 === str1) return true;
  } catch (e) {}

  return false;
};

// --- [1] Passkey 등록 옵션 생성 ---
export const generateRegisterOptions = onCall(async (request) => {
  const email = normalizeEmail(request.data.email);
  if (!email) {
      throw new HttpsError('invalid-argument', '이메일은 필수 입력 항목입니다.');
  }

  try {
      const userRef = db.collection('passkey_users').doc(email);
      const userDoc = await userRef.get();

      let userIdBuffer;

      if (!userDoc.exists) {
          userIdBuffer = Buffer.from(email);
          await userRef.set({ userId: userIdBuffer.toString('base64url'), devices: [] });
      } else {
          const storedUserId = userDoc.data().userId;
          userIdBuffer = storedUserId ? Buffer.from(storedUserId, 'base64url') : Buffer.from(email);
      }

      const options = await generateRegistrationOptions({
          rpName,
          rpID,
          userID: userIdBuffer,
          userName: email,
          authenticatorSelection: {
              residentKey: 'required',
              userVerification: 'required',
          },
      });

      const responseOptions = {
          ...options,
          rpId: rpID,
          rpID: rpID,
      };

      await userRef.set({ currentChallenge: options.challenge }, { merge: true });
      return responseOptions;
  } catch (error) {
      logger.error("등록 옵션 생성 중 에러:", error);
      rethrowHttpsError(error);
  }
});

// --- [2] Passkey 등록 검증 ---
export const verifyRegister = onCall(async (request) => {
   let { credential } = request.data;
   const requestChallenge = typeof request.data.challenge === 'string' ? request.data.challenge : null;
   const email = normalizeEmail(request.data.email);
   if (!email || !credential) {
       throw new HttpsError('invalid-argument', '이메일과 인증 정보가 필요합니다.');
   }

   try {
       if (typeof credential === 'string') {
           credential = JSON.parse(credential);
       }

       logger.info("파싱된 credential 객체 확인:", credential);

       const userRef = db.collection('passkey_users').doc(email);
       const userDoc = await userRef.get();
       const userData = userDoc.exists ? userDoc.data() : null;
       const expectedChallenge = userData?.currentChallenge || requestChallenge;
       if (!expectedChallenge) {
           throw new HttpsError('failed-precondition', '진행 중인 등록 챌린지가 없습니다. 다시 등록을 시작해주세요.');
       }

       const formattedCredential = {
           id: credential.id || credential.rawId,
           rawId: credential.rawId || credential.id,
           type: credential.type || 'public-key',
           response: {
               clientDataJSON: credential.response?.clientDataJSON,
               attestationObject: credential.response?.attestationObject,
               transports: credential.response?.transports,
           }
       };

       const verification = await verifyRegistrationResponse({
           response: formattedCredential,
           expectedChallenge,
           expectedOrigin,
           expectedRPID: rpID,
       });

       if (verification.verified && verification.registrationInfo) {
           const { registrationInfo } = verification;
           const cred = registrationInfo.credential;

           const credentialIDStr = cred?.id || safeBase64UrlString(registrationInfo.credentialID);
           const credentialPublicKeyStr = safeBase64UrlString(cred?.publicKey || registrationInfo.credentialPublicKey);
           const counter = cred?.counter ?? registrationInfo.counter ?? 0;

           const userIdBuffer = userData?.userId ? Buffer.from(userData.userId, 'base64url') : Buffer.from(email);
           const userIdString = userIdBuffer.toString('base64url');

           const newDevice = {
               credentialID: credentialIDStr,
               credentialPublicKey: credentialPublicKeyStr,
               counter,
           };

           await userRef.set({
               userId: userIdString,
               devices: FieldValue.arrayUnion(newDevice),
               currentChallenge: null,
           }, { merge: true });

           let uid;
           try {
               const userRecord = await auth.getUserByEmail(email);
               uid = userRecord.uid;
           } catch (e) {
               const userRecord = await auth.createUser({ email });
               uid = userRecord.uid;
           }

           const customToken = await auth.createCustomToken(uid);
           return { verified: true, customToken };
       } else {
           throw new HttpsError('permission-denied', 'Passkey 검증에 실패했습니다.');
       }
   } catch (error) {
       logger.error("등록 검증 중 에러:", error);
       rethrowHttpsError(error);
   }
});

// --- [3] Passkey 로그인 옵션 생성 ---
export const generateAuthOptions = onCall(async (request) => {
  const email = normalizeEmail(request.data.email);
  const discoverableOnly = request.data?.discoverableOnly === true;
  let allowCredentials = [];

  if (email && !discoverableOnly) {
    const userRef = db.collection('passkey_users').doc(email);
    const userDoc = await userRef.get();
    if (userDoc.exists && Array.isArray(userDoc.data().devices) && userDoc.data().devices.length > 0) {
      allowCredentials = userDoc.data().devices.map(dev => ({
        id: safeBase64UrlString(dev.credentialID),
        type: 'public-key',
      })).filter(dev => Boolean(dev.id));
    }
  }

  const options = await generateAuthenticationOptions({
    rpID,
    allowCredentials: allowCredentials.length > 0 ? allowCredentials : undefined,
    userVerification: 'required',
  });

  logger.info('generateAuthOptions summary', {
    email: email ?? null,
    rpID,
    discoverableOnly,
    allowCredentialsCount: allowCredentials.length,
    challengeLength: options.challenge?.length ?? 0,
  });

  const responseOptions = {
    ...options,
    rpId: rpID,
    rpID: rpID,
  };

  if (email) {
    const userRef = db.collection('passkey_users').doc(email);
    await userRef.set({ currentChallenge: options.challenge }, { merge: true });
  }

  return responseOptions;
});

// --- [4] Passkey 로그인 검증 및 커스텀 토큰 발급 ---
export const verifyAuth = onCall(async (request) => {
  const email = normalizeEmail(request.data.email);
  const { credential } = request.data;
  if (!credential) {
    throw new HttpsError('invalid-argument', '인증 정보가 필요합니다.');
  }

  let credObj = credential;
  if (typeof credObj === 'string') {
    try {
      credObj = JSON.parse(credObj);
    } catch (e) {}
  }

  const requestRawId = credObj.rawId || credObj.id;

  let userRef, userData, targetEmail = email;

  // 1) Direct lookup by email
  if (targetEmail) {
    userRef = db.collection('passkey_users').doc(targetEmail);
    const userDoc = await userRef.get();
    if (userDoc.exists) {
      userData = userDoc.data();
    }
  }

  // 2) Lookup by userHandle embedded in WebAuthn response
  if (!userData && credObj?.response?.userHandle) {
    try {
      const decodedUserHandle = Buffer.from(credObj.response.userHandle, 'base64url').toString('utf-8');
      const handleEmail = normalizeEmail(decodedUserHandle);
      if (handleEmail) {
        const handleDoc = await db.collection('passkey_users').doc(handleEmail).get();
        if (handleDoc.exists) {
          targetEmail = handleEmail;
          userRef = handleDoc.ref;
          userData = handleDoc.data();
        }
      }
    } catch (e) {}
  }

  // 3) Lookup by matching credentialID across passkey_users collection
  if (!userData && requestRawId) {
    const snapshot = await db.collection('passkey_users').get();
    for (const doc of snapshot.docs) {
      const data = doc.data();
      if (Array.isArray(data.devices)) {
        const found = data.devices.find((d) => credentialIdsMatch(d.credentialID, requestRawId));
        if (found) {
          targetEmail = doc.id;
          userRef = doc.ref;
          userData = data;
          break;
        }
      }
    }
  }

  if (!userData) {
    throw new HttpsError('not-found', '등록된 패스키 사용자 정보를 찾을 수 없습니다.');
  }

  if (!userData.currentChallenge) {
    throw new HttpsError('failed-precondition', '진행 중인 로그인 챌린지가 없습니다. 다시 시도해주세요.');
  }

  // Find matching device record for authentication verification
  const matchedDevice = Array.isArray(userData.devices)
    ? userData.devices.find((d) => credentialIdsMatch(d.credentialID, requestRawId))
    : null;

  logger.info('verifyAuth credential lookup', {
    email: targetEmail,
    requestRawId,
    devicesCount: Array.isArray(userData.devices) ? userData.devices.length : 0,
    matched: Boolean(matchedDevice),
  });

  if (!matchedDevice) {
    throw new HttpsError('not-found', '등록된 패스키 기기 정보를 찾을 수 없습니다.');
  }

  try {
    const verification = await verifyAuthenticationResponse({
      response: credObj,
      expectedChallenge: userData.currentChallenge,
      expectedOrigin,
      expectedRPID: rpID,
      authenticator: {
        credentialPublicKey: toPublicKeyBuffer(matchedDevice.credentialPublicKey),
        credentialID: toCredentialIdBuffer(requestRawId || matchedDevice.credentialID),
        counter: matchedDevice.counter,
      },
    });

    if (verification.verified) {
      matchedDevice.counter = verification.authenticationInfo.newCounter;
      await userRef.update({
        devices: userData.devices,
        currentChallenge: null
      });

      const userRecord = await auth.getUserByEmail(targetEmail);
      const customToken = await auth.createCustomToken(userRecord.uid);

      return { verified: true, customToken };
    } else {
      throw new HttpsError('permission-denied', '인증 검증에 실패했습니다.');
    }
  } catch (error) {
    logger.error('로그인 검증 중 에러:', error);
    rethrowHttpsError(error);
  }
});
