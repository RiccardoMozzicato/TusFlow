package g11.tusuploaderservice.dto

data class RagQueryRequest(
    val query: String,
    val filters: Map<String, String> = emptyMap(),
    val topK: Int? = 5
)

data class RagQueryResponse(
    val query: String,
    val answer: String
)

