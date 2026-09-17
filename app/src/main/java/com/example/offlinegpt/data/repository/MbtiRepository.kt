package com.example.offlinegpt.data.repository

import com.example.offlinegpt.data.local.MBTIResult
import com.example.offlinegpt.data.local.MbtiDao
import com.example.offlinegpt.data.mbti.MBTIQuestion
import com.example.offlinegpt.data.mbti.MbtiTrait
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MbtiRepository @Inject constructor(
    private val mbtiDao: MbtiDao
) {
    fun getQuestions(): List<MBTIQuestion> = mbtiQuestions

    suspend fun saveResult(result: MBTIResult) {
        mbtiDao.insertResult(result)
    }

    fun getLatestResult(userEmail: String): Flow<MBTIResult?> {
        return mbtiDao.getLatestResult(userEmail)
    }

    fun getHistory(userEmail: String): Flow<List<MBTIResult>> {
        return mbtiDao.getAllResults(userEmail)
    }

    companion object {
        private val mbtiQuestions = listOf(
            // E vs I
            MBTIQuestion(1, "나는 새로운 사람들과 만나는 것이 즐겁다.", MbtiTrait.E_I, 1),
            MBTIQuestion(2, "나는 혼자 있는 시간보다 사람들과 함께 있을 때 에너지를 얻는다.", MbtiTrait.E_I, 1),
            MBTIQuestion(3, "나는 말하기 전에 생각하기보다 생각하면서 말하는 편이다.", MbtiTrait.E_I, 1),
            MBTIQuestion(4, "나는 조용한 환경보다 활기찬 분위기를 선호한다.", MbtiTrait.E_I, 1),
            MBTIQuestion(5, "나는 주말에 집에서 쉬는 것보다 밖에서 활동하는 것이 좋다.", MbtiTrait.E_I, 1),
            MBTIQuestion(6, "나는 다수의 사람들과 어울리는 자리가 편안하다.", MbtiTrait.E_I, 1),

            // S vs N
            MBTIQuestion(7, "나는 실제적인 경험과 사실을 더 신뢰한다.", MbtiTrait.S_N, 1),
            MBTIQuestion(8, "나는 상상력보다는 현실적인 해결책을 선호한다.", MbtiTrait.S_N, 1),
            MBTIQuestion(9, "나는 세부적인 사항까지 꼼꼼하게 챙기는 편이다.", MbtiTrait.S_N, 1),
            MBTIQuestion(10, "나는 미래의 가능성보다 현재의 안정감을 더 중요하게 생각한다.", MbtiTrait.S_N, 1),
            MBTIQuestion(11, "나는 창의적인 아이디어보다 검증된 방법이 더 좋다.", MbtiTrait.S_N, 1),
            MBTIQuestion(12, "나는 추상적인 개념보다 명확하고 구체적인 정보를 선호한다.", MbtiTrait.S_N, 1),

            // T vs F
            MBTIQuestion(13, "나는 결정할 때 감정보다 논리적인 근거를 더 중시한다.", MbtiTrait.T_F, 1),
            MBTIQuestion(14, "나는 공감해주는 것보다 객관적인 조언을 해주는 것이 더 중요하다고 믿는다.", MbtiTrait.T_F, 1),
            MBTIQuestion(15, "나는 갈등 상황에서 공정함이 인간관계보다 우선이라고 생각한다.", MbtiTrait.T_F, 1),
            MBTIQuestion(16, "나는 비판을 받았을 때 감정적으로 상처받기보다 논리적으로 이해하려고 노력한다.", MbtiTrait.T_F, 1),
            MBTIQuestion(17, "나는 타인의 기분을 맞추기 위해 내 원칙을 굽히지 않는다.", MbtiTrait.T_F, 1),
            MBTIQuestion(18, "나는 따뜻한 위로보다 문제 해결을 위한 분석이 더 도움이 된다고 생각한다.", MbtiTrait.T_F, 1),

            // J vs P
            MBTIQuestion(19, "나는 일을 시작하기 전에 계획을 세밀하게 세우는 편이다.", MbtiTrait.J_P, 1),
            MBTIQuestion(20, "나는 즉흥적인 것보다 정해진 일정대로 움직이는 것이 편하다.", MbtiTrait.J_P, 1),
            MBTIQuestion(21, "나는 마감 기한이 닥쳐서 서두르기보다 미리미리 끝내두는 것을 선호한다.", MbtiTrait.J_P, 1),
            MBTIQuestion(22, "나는 정리정돈된 환경에서 더 효율적으로 일할 수 있다.", MbtiTrait.J_P, 1),
            MBTIQuestion(23, "나는 여행을 갈 때 상세한 일정을 미리 정해둔다.", MbtiTrait.J_P, 1),
            MBTIQuestion(24, "나는 변수가 생기는 상황보다 계획된 상황을 더 선호한다.", MbtiTrait.J_P, 1)
        )
    }
}
