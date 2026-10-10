package com.example.offlinegpt.data.mbti

object MbtiDescriptions {
    fun getDescription(type: String): String {
        return when (type.uppercase()) {
            "INTJ" -> "Architect: Imaginative and strategic thinkers, with a plan for everything. Curious yet highly decisive, they channel their energy with great focus."
            "INTP" -> "Logician: Innovative inventors with an unquenchable thirst for knowledge. They constantly seek new insights and thrive on analytical thinking."
            "ENTJ" -> "Commander: Bold, imaginative, and strong-willed leaders. They always find or create a path forward, placing high value on efficiency."
            "ENTP" -> "Debater: Smart and curious thinkers who cannot resist an intellectual challenge. They enjoy breaking down conventional wisdom and testing boundaries."
            "INFJ" -> "Advocate: Quiet and mystical, yet inspiring and tireless idealists. They create structured plans to turn their core values into reality."
            "INFP" -> "Mediator: Poised and altruistic idealists, always ready to champion a good cause. Possessing strong inner convictions and deep personal insight."
            "ENFJ" -> "Protagonist: Charismatic and inspiring leaders who captivate their audience. They find great fulfillment in guiding others and creating harmony."
            "ENFP" -> "Campaigner: Enthusiastic, creative, and sociable free spirits who can always find a reason to smile. They connect deeply with others through positive energy."
            "ISTJ" -> "Logistician: Practical and fact-minded individuals whose reliability cannot be doubted. Responsible, organized, and deeply realistic."
            "ISFJ" -> "Defender: Dedicated and warm protectors, always ready to defend their loved ones. Meticulous, reliable, and highly attuned to others' feelings."
            "ESTJ" -> "Executive: Excellent administrators, unsurpassed at managing things or people. They value order, rules, and practical execution."
            "ESFJ" -> "Consul: Extraordinarily caring, social, and eager to help. They prioritize cooperation and excel at fostering warm community connections."
            "ISTP" -> "Virtuoso: Bold and practical experimenters, masters of all kinds of tools. Adaptable and calm under pressure, they thrive on hands-on experiences."
            "ISFP" -> "Adventurer: Flexible and charming artists, always ready to explore and experience something new. They live in the present with strong aesthetic awareness."
            "ESTP" -> "Entrepreneur: Energetic, perceptive, and action-oriented risk-takers. Smart and intuitive, they prefer quick, practical solutions to complex problems."
            "ESFP" -> "Entertainer: Spontaneous, energetic, and enthusiastic people—life is never boring around them. They love engaging with people and sharing bright experiences."
            else -> "Unable to analyze results."
        }
    }
}