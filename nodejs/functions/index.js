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
const db = getFirestore('webauthn');
const auth = getAuth();

// 설정
const rpName = 'OfflineGPT';
const rpID = 'offlinegpt-dev.web.app';
// 안드로이드 디버그 빌드 서명 해시가 적용된 오리진
const expectedOrigin = 'android:apk-key-hash:KNLgEk0CUg8ZaHzApFUKhVOgxP5IYuFF-JbVJ1E0S3U';

// --- [1] Passkey 등록 옵션 생성 ---
export const generateRegisterOptions = onCall(async (request) => {
  const email = request.data.email;
      if (!email) {
          throw new HttpsError('invalid-argument', '이메일은 필수 입력 항목입니다.');
      }

      try {
          const userRef = db.collection('passkey_users').doc(email);
          const userDoc = await userRef.get();

          let userIdBuffer;
          let userIdString;

          if (!userDoc.exists) {
              // 이메일을 기반으로 Buffer(바이너리) 생성
              userIdBuffer = Buffer.from(email);
              userIdString = userIdBuffer.toString('base64'); // Firestore 저장용
              await userRef.set({ userId: userIdString, devices: [] });
          } else {
              userIdString = userDoc.data().userId;
              // Firestore에 저장된 문자열을 다시 Buffer(바이너리)로 복원
              userIdBuffer = Buffer.from(userIdString, 'base64');
          }

          const options = await generateRegistrationOptions({
              rpName,
              rpID,
              // @simplewebauthn 규격에 맞게 Buffer (Uint8Array) 형태로 전달
              userID: userIdBuffer,
              userName: email,
          });

          await userRef.update({ currentChallenge: options.challenge });
          return options;
      } catch (error) {
          logger.error("등록 옵션 생성 중 에러:", error);
          throw new HttpsError('internal', error.message);
      }
});

// --- [3] Passkey 로그인 옵션 생성 ---
export const generateAuthOptions = onCall(async (request) => {
  // 사용자가 누구인지 특정할 수 있다면 email을 받고,
  // 이메일 없이 디바이스의 패스키 목록으로 전역 로그인을 하려면 email을 생략할 수도 있습니다.
  const { email } = request.data;

  let allowCredentials = [];

  if (email) {
    const userRef = db.collection('passkey_users').doc(email);
    const userDoc = await userRef.get();
    if (userDoc.exists && userDoc.data().devices) {
      // 등록된 기기(Credential ID) 목록을 allowCredentials에 담아 특정 기기만 인증 유도
      allowCredentials = userDoc.data().devices.map(dev => ({
        id: Buffer.from(dev.credentialID, 'base64'),
        type: 'public-key',
        transports: ['internal'] // 안드로이드 기기 내장 패스키
      }));
    }
  }

  const options = await generateAuthenticationOptions({
    rpID,
    allowCredentials,
    userVerification: 'preferred',
  });

  // 챌린지 임시 저장 (이메일이 있는 경우 해당 문서에, 혹은 임시 세션에 저장)
  if (email) {
    await db.collection('passkey_users').doc(email).update({ currentChallenge: options.challenge });
  } else {
    // 이메일 없이 전역 로그인을 지원하려면 challenge를 별도 컬렉션에 관리해야 합니다.
    await db.collection('passkey_challenges').doc(options.challenge).set({ createdAt: Date.now() });
  }

  return options;
});

// --- [4] Passkey 로그인 검증 및 커스텀 토큰 발급 ---
export const verifyAuth = onCall(async (request) => {
  const { email, credential } = request.data;
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
  } else {
    // 이메일 없이 로그인한 경우 credentialID로 사용자를 역추적해야 합니다.
    // 여기서는 간단하게 이메일이 필수로 전달되는 시나리오를 기준으로 합니다.
    throw new HttpsError('invalid-argument', '이메일 정보가 필요합니다.');
  }

  // 저장된 퍼블릭 키 찾기
  const rawId = credObj.rawId || credObj.id;
  const matchedDevice = userData.devices.find(d => d.credentialID === rawId || Buffer.from(d.credentialID, 'base64').toString('base64') === rawId);

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
        credentialPublicKey: Buffer.from(matchedDevice.credentialPublicKey, 'base64'),
        credentialID: Buffer.from(matchedDevice.credentialID, 'base64'),
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
    throw new HttpsError('internal', error.message);
  }
});
