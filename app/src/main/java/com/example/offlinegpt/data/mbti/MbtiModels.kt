package com.example.offlinegpt.data.mbti

enum class MbtiTrait {
    E_I, S_N, T_F, J_P
}

data class MBTIQuestion(
    val id: Int,
    val text: String,
    val trait: MbtiTrait,
    val direction: Int // 1 if "Yes" means E/S/T/J, -1 if "Yes" means I/N/F/P
)

data class MBTIScore(
    var extroversion: Int = 0,
    var introversion: Int = 0,
    var sensing: Int = 0,
    var intuition: Int = 0,
    var thinking: Int = 0,
    var feeling: Int = 0,
    var judging: Int = 0,
    var perceiving: Int = 0
)
