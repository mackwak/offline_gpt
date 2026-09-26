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

const normalizeCredentialId = (value) => {
  if (!value || typeof value !== 'string') return null;
  try {
    return Buffer.from(value, 'base64url').toString('base64url');
  } catch (e) {
    return null;
  }
};

const toBufferFromEncoded = (value) => {
  const normalized = normalizeCredentialId(value);
  if (!normalized) {
    throw new HttpsError('invalid-argument', 'credential 인코딩 복원에 실패했습니다.');
  }
  return Buffer.from(normalized, 'base64url');
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

          await userRef.set({ currentChallenge: options.challenge }, { merge: true });
          return options;
      } catch (error) {
          logger.error("등록 옵션 생성 중 에러:", error);
          rethrowHttpsError(error);
      }
});

export const verifyRegister = onCall(async (request) => {

   let { credential } = request.data;
       const requestChallenge = typeof request.data.challenge === 'string' ? request.data.challenge : null;
       const email = normalizeEmail(request.data.email);
       if (!email || !credential) {
           throw new HttpsError('invalid-argument', '이메일과 인증 정보가 필요합니다.');
       }

       try {
           // 만약 credential이 JSON 문자열 형태라면 객체로 파싱
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

           // 표준 포맷으로 매핑
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
               const { credentialPublicKey, credentialID, counter } = verification.registrationInfo;
               const userIdBuffer = userData?.userId ? Buffer.from(userData.userId, 'base64url') : Buffer.from(email);
               const userIdString = userIdBuffer.toString('base64url');

               const newDevice = {
                   credentialID: Buffer.from(credentialID).toString('base64url'),
                   credentialPublicKey: Buffer.from(credentialPublicKey).toString('base64url'),
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
  let allowCredentials = [];

  if (email) {
    const userRef = db.collection('passkey_users').doc(email);
    const userDoc = await userRef.get();
    if (userDoc.exists && userDoc.data().devices && userDoc.data().devices.length > 0) {
      allowCredentials = userDoc.data().devices.map(dev => ({
        id: normalizeCredentialId(dev.credentialID),
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

  let userRef, userData, targetEmail = email;

  if (targetEmail) {
    userRef = db.collection('passkey_users').doc(targetEmail);
    const userDoc = await userRef.get();
    if (!userDoc.exists) throw new HttpsError('not-found', '사용자를 찾을 수 없습니다.');
    userData = userDoc.data();
    if (!userData.currentChallenge) {
      throw new HttpsError('failed-precondition', '진행 중인 로그인 챌린지가 없습니다. 다시 시도해주세요.');
    }
  } else {
    // 이메일 없이 로그인한 경우 credentialID로 사용자를 역추적해야 합니다.
    // 여기서는 간단하게 이메일이 필수로 전달되는 시나리오를 기준으로 합니다.
    throw new HttpsError('invalid-argument', '이메일 정보가 필요합니다.');
  }

  // 저장된 퍼블릭 키 찾기
  const rawId = credObj.rawId || credObj.id;
  const normalizedRawId = normalizeCredentialId(rawId);
  const matchedDevice = userData.devices.find((d) => normalizeCredentialId(d.credentialID) === normalizedRawId);

  logger.info('verifyAuth credential lookup', {
    email: targetEmail,
    hasRawId: Boolean(rawId),
    normalizedRawIdPrefix: normalizedRawId?.slice(0, 12) ?? null,
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
        credentialPublicKey: toBufferFromEncoded(matchedDevice.credentialPublicKey),
        credentialID: toBufferFromEncoded(matchedDevice.credentialID),
        counter: matchedDevice.counter,
      },
    });

    if (verification.verified) {
      // 카운터 갱신 (리플레이 공격 방지)
      matchedDevice.counter = verification.authenticationInfo.newCounter;
      await userRef.update({
        devices: userData.devices,
        currentChallenge: null
      });

      // Firebase 사용자 조회 후 Custom Token 발급
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
