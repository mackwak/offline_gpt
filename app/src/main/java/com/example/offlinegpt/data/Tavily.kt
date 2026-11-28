package com.example.offlinegpt.data

data class TavilySearchRequest(
    val query: String,
    val search_depth: String = "basic", // "basic" or "advanced"
    val max_results: Int = 5,
    val include_answer: Boolean = true
)

data class TavilyResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double
)