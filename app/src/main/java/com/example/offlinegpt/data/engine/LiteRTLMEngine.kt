package com.example.offlinegpt.data.engine

import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class LiteRTLMEngine {

    private var engine: Engine? = null
    private var activeConversation: Conversation? = null

    /**
     * Initializes the LiteRT-LM runtime on an I/O thread.
     * @param modelPath Absolute file path to your .litertlm model file.
     */
    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        val config = EngineConfig(
            modelPath = modelPath,
            // Additional settings like backend selection or MTP can be set here
        )

        // Initialize Native Engine & Chat Session
        engine = Engine(config).apply {
            initialize()
            activeConversation = createConversation()
        }
    }

    /**
     * Sends a message and streams back generated tokens via Kotlin Flow.
     */


    fun sendMessageStream(prompt: String): Flow<Message> {
        val conversation = activeConversation
            ?: throw IllegalStateException("LiteRT-LM Engine is not initialized.")

        return conversation.sendMessageAsync(prompt)
    }


    /**
     * Resets current chat history / clears KV-cache state.
     */
    fun resetConversation() {
        activeConversation?.close()
        activeConversation = engine?.createConversation()
    }

    /**
     * Releases C++ native memory allocations.
     */
    fun close() {
        activeConversation?.close()
        activeConversation = null
        engine?.close()
        engine = null
    }
}