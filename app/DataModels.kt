// Structura pentru cererea de chat
data class ChatRequest(
    val model: String = "deepseek-chat", // Modelul corect
    val messages: List<Message>,
    val temperature: Double = 0.7
)

// Structura pentru fiecare mesaj trimis
data class Message(
    val role: String = "user",  // Poate fi 'user' sau 'assistant'
    val content: String         // Conținutul mesajului
)

// Structura pentru răspunsul API-ului
data class ChatResponse(
    val choices: List<Choice>
)

// Structura pentru opțiunile de răspuns
data class Choice(
    val message: Message
)
