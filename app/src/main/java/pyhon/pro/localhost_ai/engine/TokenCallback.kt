package pyhon.pro.localhost_ai.engine

/**
 * Callback invoked during native LLM generation for each generated token chunk.
 */
interface TokenCallback {
    /**
     * @param token The decoded text piece of the current token.
     * @param isFinished True if generation has completed (EOS reached or max tokens).
     * @return True to continue generation, False to abort.
     */
    fun onToken(token: String, isFinished: Boolean): Boolean
}
