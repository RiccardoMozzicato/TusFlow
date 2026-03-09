package g11.aiprocessingservice.conf

import org.springframework.ai.document.Document
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.stereotype.Component

@Component
class MyTokenTextSplitter {
    fun splitDocuments(documents: Document): MutableList<Document> {
        val splitter = TokenTextSplitter()
        return splitter.split(documents)
    }
}
