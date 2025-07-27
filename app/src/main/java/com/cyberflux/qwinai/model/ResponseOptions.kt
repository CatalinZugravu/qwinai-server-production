package com.cyberflux.qwinai.model

// Response length options
enum class ResponseLength(val displayName: String, val description: String) {
    DEFAULT("Default", "Standard response length"),
    SHORT("Short", "Brief, concise responses"),
    LONG("Long", "Detailed, comprehensive responses")
}

// Response tone options
enum class ResponseTone(val displayName: String, val description: String) {
    DEFAULT("Default", "Standard conversational tone"),
    PROFESSIONAL("Professional", "Formal, business-like tone"),
    FRIENDLY("Friendly", "Warm, approachable tone"),
    ROMANTIC("Romantic", "Poetic, emotional tone"),
    INSPIRATIONAL("Inspirational", "Motivational, encouraging tone"),
    PASSIONATE("Passionate", "Enthusiastic, energetic tone"),
    PERSUASIVE("Persuasive", "Convincing, compelling tone"),
    CRITICAL("Critical", "Analytical, evaluative tone"),
    JOYFUL("Joyful", "Happy, cheerful tone"),
    SCIENTIFIC("Scientific", "Academic, precise tone"),
    CREATIVE("Creative", "Imaginative, artistic tone"),
    HUMOROUS("Humorous", "Funny, witty tone"),

    // Emotional Tones
    EMPATHETIC("Empathetic", "Understanding and sharing feelings"),
    SYMPATHETIC("Sympathetic", "Showing compassion and concern"),
    NOSTALGIC("Nostalgic", "Wistful, reminiscent of the past"),
    MELANCHOLIC("Melancholic", "Gentle sadness or pensiveness"),
    EXCITED("Excited", "Enthusiastic and eager"),
    CALM("Calm", "Peaceful, tranquil tone"),
    REASSURING("Reassuring", "Comforting, supportive tone"),
    OPTIMISTIC("Optimistic", "Hopeful, positive outlook"),
    PESSIMISTIC("Pessimistic", "Expecting negative outcomes"),
    GRATEFUL("Grateful", "Expressing appreciation"),
    VULNERABLE("Vulnerable", "Open, honest about weaknesses"),
    CONFIDENT("Confident", "Self-assured, certain tone"),

    // Communication Styles
    CASUAL("Casual", "Relaxed, informal tone"),
    DIPLOMATIC("Diplomatic", "Tactful, considerate tone"),
    DIRECT("Direct", "Straightforward, candid tone"),
    TECHNICAL("Technical", "Specialized vocabulary, precise"),
    EDUCATIONAL("Educational", "Instructive, informative tone"),
    SIMPLIFIED("Simplified", "Easy to understand, avoids jargon"),
    PHILOSOPHICAL("Philosophical", "Contemplative, thought-provoking"),
    JOURNALISTIC("Journalistic", "Factual, news-like tone"),
    NARRATIVE("Narrative", "Story-telling style"),
    POETIC("Poetic", "Lyrical, using figurative language"),
    CONVERSATIONAL("Conversational", "Natural, dialogue-like tone"),
    ELEGANT("Elegant", "Refined, sophisticated tone"),

    // Functional Tones
    AUTHORITATIVE("Authoritative", "Commanding, expert tone"),
    INQUISITIVE("Inquisitive", "Questioning, curious tone"),
    INSTRUCTIONAL("Instructional", "Step-by-step guidance"),
    ADVISING("Advising", "Offering suggestions or counsel"),
    SUMMARIZING("Summarizing", "Concise overview"),
    REFLECTIVE("Reflective", "Thoughtful consideration"),
    URGENT("Urgent", "Conveying time-sensitivity"),
    CAUTIONARY("Cautionary", "Warning of potential issues"),
    SUPPORTIVE("Supportive", "Encouraging, backing up ideas"),
    CHALLENGING("Challenging", "Questioning assumptions"),
    DESCRIPTIVE("Descriptive", "Vividly portraying details"),
    ANALYTICAL("Analytical", "Breaking down complex ideas"),

    // Specialized Tones
    LEGAL("Legal", "Formal language used in legal contexts"),
    MEDICAL("Medical", "Clinical, healthcare-oriented"),
    SPIRITUAL("Spiritual", "Contemplative, mindful tone"),
    ACADEMIC("Academic", "Scholarly, research-oriented"),
    WHIMSICAL("Whimsical", "Playfully quaint or fanciful"),
    SARDONIC("Sardonic", "Grimly mocking or cynical"),
    RESPECTFUL("Respectful", "Showing high regard"),
    IRONIC("Ironic", "Using words to express opposite meaning"),
    MYSTERIOUS("Mysterious", "Enigmatic, creating intrigue"),
    ASSERTIVE("Assertive", "Confident without aggression"),
    CONTEMPLATIVE("Contemplative", "Deep in thought"),
    MATTER_OF_FACT("Matter-of-fact", "Plain, unemotional delivery"),

    // Cultural/Stylistic Tones
    FORMAL("Formal", "Adhering to conventions, proper"),
    COLLOQUIAL("Colloquial", "Using everyday language"),
    ELOQUENT("Eloquent", "Fluent, persuasive expression"),
    MINIMALIST("Minimalist", "Simple, using few words"),
    DETAILED("Detailed", "Thorough, comprehensive"),
    SOCRATIC("Socratic", "Using questions to stimulate critical thinking"),
    LITERARY("Literary", "Sophisticated writing style"),
    CANDID("Candid", "Frank, honest, unguarded"),
    CEREMONIAL("Ceremonial", "Formal, ritualistic tone"),
    INCLUSIVE("Inclusive", "Welcoming diverse perspectives"),
    OBJECTIVE("Objective", "Unbiased, fact-based"),
    SUBJECTIVE("Subjective", "Personal, opinion-based"),

    // Intensity Levels
    GENTLE("Gentle", "Soft, mild tone"),
    MODERATE("Moderate", "Balanced, middle-ground tone"),
    INTENSE("Intense", "Strong, powerful tone"),
    RESTRAINED("Restrained", "Controlled, holding back"),
    BOLD("Bold", "Confident, taking risks"),
    SUBTLE("Subtle", "Delicate, nuanced tone"),
    DRAMATIC("Dramatic", "Theatrical, emphasizing for effect"),
    // New Additional Tones
    HISTORICAL("Historical", "Language reminiscent of a specific time period"),
    FUTURISTIC("Futuristic", "Forward-looking, technology-oriented language"),
    CHILDLIKE("Childlike", "Simple, wonder-filled language for younger audiences"),
    STOIC("Stoic", "Emotionally disciplined, rational language"),
    SARCASTIC("Sarcastic", "Ironic language for humor or light mockery"),
    MENTORING("Mentoring", "Guiding but empowering tone from experience"),
    TACTICAL("Tactical", "Precise, action-oriented language focused on execution"),
    DIALECTICAL("Dialectical", "Explores and reconciles opposing viewpoints")



}

// Class to store response preferences
data class ResponsePreferences(
    var modelPosition: Int = 0,
    var length: ResponseLength = ResponseLength.DEFAULT,
    var tone: ResponseTone = ResponseTone.DEFAULT
)