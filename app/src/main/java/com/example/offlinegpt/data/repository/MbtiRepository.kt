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
            MBTIQuestion(1, "I enjoy meeting new people.", MbtiTrait.E_I, 1),
            MBTIQuestion(2, "I gain energy from being with people rather than spending time alone.", MbtiTrait.E_I, 1),
            MBTIQuestion(3, "I tend to speak while thinking rather than thinking before speaking.", MbtiTrait.E_I, 1),
            MBTIQuestion(4, "I prefer lively atmospheres over quiet environments.", MbtiTrait.E_I, 1),
            MBTIQuestion(5, "I prefer outdoor activities on weekends over resting at home.", MbtiTrait.E_I, 1),
            MBTIQuestion(6, "I feel comfortable in large gatherings.", MbtiTrait.E_I, 1),

// S vs N
            MBTIQuestion(7, "I trust practical experience and facts more.", MbtiTrait.S_N, 1),
            MBTIQuestion(8, "I prefer realistic solutions over imagination.", MbtiTrait.S_N, 1),
            MBTIQuestion(9, "I tend to pay close attention to fine details.", MbtiTrait.S_N, 1),
            MBTIQuestion(10, "I value present stability more than future possibilities.", MbtiTrait.S_N, 1),
            MBTIQuestion(11, "I prefer proven methods over creative ideas.", MbtiTrait.S_N, 1),
            MBTIQuestion(12, "I prefer clear and concrete information over abstract concepts.", MbtiTrait.S_N, 1),

// T vs F
            MBTIQuestion(13, "I prioritize logical evidence over emotion when making decisions.", MbtiTrait.T_F, 1),
            MBTIQuestion(14, "I believe offering objective advice is more important than giving empathy.", MbtiTrait.T_F, 1),
            MBTIQuestion(15, "I believe fairness comes before personal relationships in conflict situations.", MbtiTrait.T_F, 1),
            MBTIQuestion(16, "When receiving criticism, I try to understand it logically rather than taking it emotionally.", MbtiTrait.T_F, 1),
            MBTIQuestion(17, "I do not compromise my principles just to accommodate others' feelings.", MbtiTrait.T_F, 1),
            MBTIQuestion(18, "I find problem-solving analysis more helpful than warm comfort.", MbtiTrait.T_F, 1),

// J vs P
            MBTIQuestion(19, "I tend to make detailed plans before starting a task.", MbtiTrait.J_P, 1),
            MBTIQuestion(20, "I feel more comfortable following a set schedule than being spontaneous.", MbtiTrait.J_P, 1),
            MBTIQuestion(21, "I prefer finishing tasks ahead of time rather than rushing as the deadline approaches.", MbtiTrait.J_P, 1),
            MBTIQuestion(22, "I work more efficiently in an organized environment.", MbtiTrait.J_P, 1),
            MBTIQuestion(23, "I set up a detailed itinerary when going on a trip.", MbtiTrait.J_P, 1),
            MBTIQuestion(24, "I prefer planned situations over unexpected variables.", MbtiTrait.J_P, 1)
        )
    }
}
