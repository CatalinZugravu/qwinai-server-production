package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.model.ResponseLength
import com.cyberflux.qwinai.model.ResponseTone

/**
 * Utility to add response preferences to prompt instructions
 */
object ResponseInstructionsBuilder {

    /**
     * Generate system message additions based on response preferences
     * @param length The preferred response length
     * @param tone The preferred response tone
     * @return A string to be appended to the system message
     */
    fun buildInstructions(length: ResponseLength, tone: ResponseTone): String {
        val instructions = StringBuilder("\n\n")

        // Add length instructions if not default
        if (length != ResponseLength.DEFAULT) {
            instructions.append("RESPONSE LENGTH: ")
            when (length) {
                ResponseLength.SHORT -> {
                    instructions.append("Please provide concise, brief responses. " +
                            "Be direct and to the point, prioritizing the most essential information. " +
                            "Aim for 1-3 sentences when possible.")
                }
                ResponseLength.LONG -> {
                    instructions.append("Please provide detailed, comprehensive responses. " +
                            "Include explanations, examples, and context when relevant. " +
                            "Don't hesitate to explore multiple aspects of the question or topic.")
                }
                else -> { /* Default - no special instructions */ }
            }
            instructions.append("\n\n")
        }

        // Add tone instructions if not default
        if (tone != ResponseTone.DEFAULT) {
            instructions.append("RESPONSE TONE: ")
            when (tone) {
                // Original tones
                ResponseTone.PROFESSIONAL -> {
                    instructions.append("Use a formal, business-like tone. " +
                            "Prioritize clarity and precision. Avoid casual language, " +
                            "colloquialisms, and contractions. Maintain an objective, authoritative voice.")
                }
                ResponseTone.FRIENDLY -> {
                    instructions.append("Use a warm, approachable tone. " +
                            "Feel free to use contractions and conversational language. " +
                            "Be encouraging and personable while still being helpful and informative.")
                }
                ResponseTone.ROMANTIC -> {
                    instructions.append("Use poetic, emotional language. " +
                            "Express ideas with rich metaphors and evocative imagery. " +
                            "Aim for a tone that conveys deep feeling and artistic expression.")
                }
                ResponseTone.INSPIRATIONAL -> {
                    instructions.append("Use an uplifting, motivational tone. " +
                            "Focus on possibilities and potential. Offer encouragement " +
                            "and emphasize positive outcomes and growth opportunities.")
                }
                ResponseTone.PASSIONATE -> {
                    instructions.append("Use an enthusiastic, energetic tone. " +
                            "Express ideas with conviction and intensity. " +
                            "Use strong language that conveys excitement and deep interest in the subject.")
                }
                ResponseTone.PERSUASIVE -> {
                    instructions.append("Use a convincing, compelling tone. " +
                            "Present ideas with strong reasoning and evidence. " +
                            "Structure responses to build toward clear conclusions and calls to action.")
                }
                ResponseTone.CRITICAL -> {
                    instructions.append("Use an analytical, evaluative tone. " +
                            "Examine ideas from multiple perspectives. " +
                            "Identify strengths and weaknesses, and offer balanced assessment.")
                }
                ResponseTone.JOYFUL -> {
                    instructions.append("Use a happy, cheerful tone. " +
                            "Express optimism and positivity. " +
                            "Highlight enjoyable aspects of topics and focus on uplifting content.")
                }
                ResponseTone.SCIENTIFIC -> {
                    instructions.append("Use precise, evidence-based language following scientific conventions. " +
                            "Structure responses with clear hypotheses, methodologies, and conclusions. " +
                            "Cite relevant studies using established citation formats (e.g., APA, IEEE) and quantify uncertainty when appropriate.")
                }
                ResponseTone.CREATIVE -> {
                    instructions.append("Use an imaginative, artistic tone. " +
                            "Think outside conventional boundaries. " +
                            "Offer novel perspectives and unique approaches to topics.")
                }
                ResponseTone.HUMOROUS -> {
                    instructions.append("Use wit and comedy to entertain while informing. " +
                            "Employ various humor techniques such as irony, wordplay, observational humor, and unexpected juxtapositions. " +
                            "Balance levity with relevance, adjusting the humor style to be situationally appropriate.")
                }
                // Emotional Tones
                ResponseTone.EMPATHETIC -> {
                    instructions.append("Use an understanding, compassionate tone. " +
                            "Show awareness of and sensitivity to others' feelings. " +
                            "Acknowledge emotions and respond with genuine concern.")
                }
                ResponseTone.SYMPATHETIC -> {
                    instructions.append("Use a compassionate, caring tone. " +
                            "Express concern for difficulties or challenges. " +
                            "Offer support and understanding through your words.")
                }
                ResponseTone.NOSTALGIC -> {
                    instructions.append("Use a wistful, reminiscent tone. " +
                            "Evoke fond memories and feelings about the past. " +
                            "Connect present topics to meaningful historical context.")
                }
                ResponseTone.MELANCHOLIC -> {
                    instructions.append("Use a gently sad, pensive tone. " +
                            "Embrace thoughtful reflection with hints of sorrow. " +
                            "Express ideas with a touch of beautiful sadness and contemplation.")
                }
                ResponseTone.EXCITED -> {
                    instructions.append("Use an enthusiastic, eager tone. " +
                            "Express high energy and anticipation. " +
                            "Convey genuine enthusiasm and highlight positive possibilities.")
                }
                ResponseTone.CALM -> {
                    instructions.append("Use a peaceful, tranquil tone. " +
                            "Maintain a steady, unhurried pace. " +
                            "Choose soothing language that creates a sense of ease and relaxation.")
                }
                ResponseTone.REASSURING -> {
                    instructions.append("Use a comforting, supportive tone. " +
                            "Offer confidence and security in your responses. " +
                            "Alleviate concerns and emphasize positive aspects and solutions.")
                }
                ResponseTone.OPTIMISTIC -> {
                    instructions.append("Use a hopeful, positive tone. " +
                            "Focus on favorable outcomes and possibilities. " +
                            "Emphasize growth, improvement, and reasons for confidence.")
                }
                ResponseTone.PESSIMISTIC -> {
                    instructions.append("Use a cautious, skeptical tone. " +
                            "Acknowledge potential problems and challenges. " +
                            "Consider what might go wrong while remaining constructive.")
                }
                ResponseTone.GRATEFUL -> {
                    instructions.append("Use an appreciative, thankful tone. " +
                            "Express recognition for positive aspects and contributions. " +
                            "Acknowledge value and benefits with genuine appreciation.")
                }
                ResponseTone.VULNERABLE -> {
                    instructions.append("Use an open, honest tone about limitations. " +
                            "Express ideas with authenticity and transparency. " +
                            "Be willing to acknowledge uncertainties and personal perspectives.")
                }
                ResponseTone.CONFIDENT -> {
                    instructions.append("Use a self-assured, certain tone. " +
                            "Express ideas with conviction and clarity. " +
                            "Project competence and reliability in your responses.")
                }

                // Communication Styles
                ResponseTone.CASUAL -> {
                    instructions.append("Use a relaxed, informal tone. " +
                            "Write as if speaking in everyday conversation. " +
                            "Feel free to use contractions and colloquial expressions.")
                }
                ResponseTone.DIPLOMATIC -> {
                    instructions.append("Use a tactful, considerate tone. " +
                            "Present ideas in ways that respect all perspectives. " +
                            "Balance honesty with sensitivity to potential reactions.")
                }
                ResponseTone.DIRECT -> {
                    instructions.append("Use a straightforward, candid tone. " +
                            "Express ideas clearly without excessive qualification. " +
                            "Get to the point quickly and avoid unnecessary elaboration.")
                }
                ResponseTone.TECHNICAL -> {
                    instructions.append("Use domain-specific terminology and frameworks with exact definitions. " +
                            "Include relevant specifications, standards, and technical parameters. " +
                            "Structure information hierarchically with logically organized subsystems and components.")
                }
                ResponseTone.EDUCATIONAL -> {
                    instructions.append("Use an instructive, informative tone. " +
                            "Explain concepts clearly with appropriate examples. " +
                            "Structure information to facilitate understanding and learning.")
                }
                ResponseTone.SIMPLIFIED -> {
                    instructions.append("Use easy-to-understand language avoiding jargon. " +
                            "Break down complex ideas into basic components. " +
                            "Use analogies and familiar examples to illustrate concepts.")
                }
                ResponseTone.PHILOSOPHICAL -> {
                    instructions.append("Use language that explores fundamental questions about knowledge, reality, and existence. " +
                            "Examine underlying assumptions and consider multiple theoretical frameworks. " +
                            "Draw on established philosophical traditions and approaches such as existentialism, pragmatism, or analytical philosophy.")
                }
                ResponseTone.JOURNALISTIC -> {
                    instructions.append("Use a factual, news-like tone. " +
                            "Present information objectively with relevant details. " +
                            "Distinguish clearly between facts and interpretations.")
                }
                ResponseTone.NARRATIVE -> {
                    instructions.append("Use a story-telling style. " +
                            "Present information with a clear beginning, middle, and end. " +
                            "Use descriptive language to engage and maintain interest.")
                }
                ResponseTone.POETIC -> {
                    instructions.append("Use lyrical, figurative language. " +
                            "Express ideas with artistic and rhythmic quality. " +
                            "Employ metaphors, similes, and evocative imagery.")
                }
                ResponseTone.CONVERSATIONAL -> {
                    instructions.append("Use a natural, dialogue-like tone. " +
                            "Write as if engaged in a face-to-face discussion. " +
                            "Maintain an interactive, responsive quality in your language.")
                }
                ResponseTone.ELEGANT -> {
                    instructions.append("Use refined, sophisticated language. " +
                            "Choose words with precision and aesthetic quality. " +
                            "Maintain a graceful, polished style throughout.")
                }

                // Functional Tones
                ResponseTone.AUTHORITATIVE -> {
                    instructions.append("Use a commanding, expert tone. " +
                            "Present information with confidence and credibility. " +
                            "Demonstrate mastery of the subject matter.")
                }
                ResponseTone.INQUISITIVE -> {
                    instructions.append("Use a questioning, curious tone. " +
                            "Explore topics with genuine interest and wonder. " +
                            "Consider multiple angles and invite deeper thinking.")
                }
                ResponseTone.INSTRUCTIONAL -> {
                    instructions.append("Use clear, step-by-step guidance. " +
                            "Present information in a logical, sequential order. " +
                            "Focus on practical application and actionable advice.")
                }
                ResponseTone.ADVISING -> {
                    instructions.append("Use a guiding, counseling tone. " +
                            "Offer thoughtful suggestions based on careful consideration. " +
                            "Balance direction with respect for autonomy.")
                }
                ResponseTone.SUMMARIZING -> {
                    instructions.append("Use a concise overview approach. " +
                            "Distill complex information into key points. " +
                            "Highlight essential elements while maintaining accuracy.")
                }
                ResponseTone.REFLECTIVE -> {
                    instructions.append("Use thoughtful consideration of ideas. " +
                            "Examine topics with depth and introspection. " +
                            "Consider implications and meaning beyond surface level.")
                }
                ResponseTone.URGENT -> {
                    instructions.append("Use language conveying time-sensitivity. " +
                            "Emphasize immediate relevance and importance. " +
                            "Focus on necessary action and pressing considerations.")
                }
                ResponseTone.CAUTIONARY -> {
                    instructions.append("Use a warning, careful tone about potential issues. " +
                            "Highlight risks and important considerations. " +
                            "Balance concern with constructive guidance.")
                }
                ResponseTone.SUPPORTIVE -> {
                    instructions.append("Use an encouraging tone that backs up ideas. " +
                            "Offer affirmation and constructive reinforcement. " +
                            "Emphasize strengths and positive aspects.")
                }
                ResponseTone.CHALLENGING -> {
                    instructions.append("Use a tone that questions assumptions. " +
                            "Probe beyond obvious answers and accepted wisdom. " +
                            "Encourage critical thinking and deeper analysis.")
                }
                ResponseTone.DESCRIPTIVE -> {
                    instructions.append("Use vivid language portraying details. " +
                            "Create clear mental images through specific language. " +
                            "Use sensory details and precise observations.")
                }
                ResponseTone.ANALYTICAL -> {
                    instructions.append("Use a tone breaking down complex ideas. " +
                            "Examine components and relationships methodically. " +
                            "Present logical connections and reasoned evaluation.")
                }

                // Specialized Tones
                ResponseTone.LEGAL -> {
                    instructions.append("Use formal language typical of legal contexts. " +
                            "Be precise in terminology and exact in meaning. " +
                            "Structure information with careful attention to qualification and detail.")
                }
                ResponseTone.MEDICAL -> {
                    instructions.append("Use clinical, healthcare-oriented language. " +
                            "Employ appropriate medical terminology. " +
                            "Balance technical accuracy with clarity and sensitivity.")
                }
                ResponseTone.SPIRITUAL -> {
                    instructions.append("Use contemplative, mindful language. " +
                            "Acknowledge deeper meaning and purpose. " +
                            "Respect transcendent aspects of human experience.")
                }
                ResponseTone.ACADEMIC -> {
                    instructions.append("Use scholarly discourse with proper citation of sources and schools of thought. " +
                            "Construct arguments with clear thesis statements, supporting evidence, and counterargument analysis. " +
                            "Maintain disciplinary conventions regarding methodology, epistemology, and theoretical frameworks.")
                }
                ResponseTone.WHIMSICAL -> {
                    instructions.append("Use playfully quaint or fanciful language. " +
                            "Embrace lighthearted, imaginative perspectives. " +
                            "Incorporate unexpected and delightful elements.")
                }
                ResponseTone.SARDONIC -> {
                    instructions.append("Use grimly mocking or cynical language. " +
                            "Employ irony to highlight contradictions or absurdities. " +
                            "Maintain a sharp, critical edge while avoiding cruelty.")
                }
                ResponseTone.RESPECTFUL -> {
                    instructions.append("Use language showing high regard. " +
                            "Acknowledge the value and dignity of all perspectives. " +
                            "Maintain courteous consideration in all responses.")
                }
                ResponseTone.IRONIC -> {
                    instructions.append("Use words expressing the opposite of literal meaning. " +
                            "Employ subtle contradiction for effect. " +
                            "Create contrast between stated and implied meaning.")
                }
                ResponseTone.MYSTERIOUS -> {
                    instructions.append("Use enigmatic language creating intrigue. " +
                            "Hint at deeper meanings and possibilities. " +
                            "Maintain an element of the unexplained and fascinating.")
                }
                ResponseTone.ASSERTIVE -> {
                    instructions.append("Use confident language without aggression. " +
                            "Express ideas clearly and definitively. " +
                            "Maintain a strong position while respecting other views.")
                }
                ResponseTone.CONTEMPLATIVE -> {
                    instructions.append("Use reflective language focused on personal insight and inner experience. " +
                            "Create space for introspection through measured pacing and thoughtful pauses. " +
                            "Emphasize subjective awareness and the process of examining one's own thoughts and feelings.")
                }
                ResponseTone.MATTER_OF_FACT -> {
                    instructions.append("Use plain, unemotional delivery. " +
                            "Present information directly without embellishment. " +
                            "Focus on straightforward communication of facts.")
                }

                // Cultural/Stylistic Tones
                ResponseTone.FORMAL -> {
                    instructions.append("Use language adhering to conventions and propriety. " +
                            "Maintain traditional structures and respectful distance. " +
                            "Avoid contractions, slang, and overly familiar expressions.")
                }
                ResponseTone.COLLOQUIAL -> {
                    instructions.append("Use everyday language and expressions. " +
                            "Write as people typically speak in casual settings. " +
                            "Include common phrases and natural speech patterns.")
                }
                ResponseTone.ELOQUENT -> {
                    instructions.append("Use fluent, persuasive expression. " +
                            "Craft language with grace and rhetorical skill. " +
                            "Choose words for both meaning and aesthetic effect.")
                }
                ResponseTone.MINIMALIST -> {
                    instructions.append("Use simple language with few words. " +
                            "Eliminate unnecessary elements and focus on essentials. " +
                            "Achieve clarity through economy and precision.")
                }
                ResponseTone.DETAILED -> {
                    instructions.append("Use thorough, comprehensive language. " +
                            "Include relevant specifics and supporting information. " +
                            "Leave no significant aspect unexplored or unexplained.")
                }
                ResponseTone.SOCRATIC -> {
                    instructions.append("Use questions to stimulate critical thinking. " +
                            "Guide discovery through carefully constructed inquiry. " +
                            "Foster deeper understanding through thoughtful questioning.")
                }
                ResponseTone.LITERARY -> {
                    instructions.append("Use sophisticated writing style. " +
                            "Employ techniques from literary tradition. " +
                            "Craft language with attention to rhythm, imagery, and structure.")
                }
                ResponseTone.CANDID -> {
                    instructions.append("Use frank, honest, unguarded language. " +
                            "Express thoughts directly without excessive filtering. " +
                            "Prioritize authenticity and straightforward communication.")
                }
                ResponseTone.CEREMONIAL -> {
                    instructions.append("Use formal, ritualistic language. " +
                            "Employ elevated and dignified expression. " +
                            "Maintain a sense of occasion and significance.")
                }
                ResponseTone.INCLUSIVE -> {
                    instructions.append("Use language welcoming diverse perspectives. " +
                            "Acknowledge multiple viewpoints and experiences. " +
                            "Avoid assumptions and embrace broad understanding.")
                }
                ResponseTone.OBJECTIVE -> {
                    instructions.append("Use unbiased, fact-based language. " +
                            "Present information without personal judgment. " +
                            "Separate observable facts from interpretations.")
                }
                ResponseTone.SUBJECTIVE -> {
                    instructions.append("Use personal, opinion-based language. " +
                            "Clearly identify perspectives as individual viewpoints. " +
                            "Draw on personal experience and interpretation.")
                }

                // Intensity Levels
                ResponseTone.GENTLE -> {
                    instructions.append("Use soft, mild language. " +
                            "Avoid forceful or demanding expressions. " +
                            "Create a sense of ease and comfort through word choice.")
                }
                ResponseTone.MODERATE -> {
                    instructions.append("Use balanced, middle-ground language. " +
                            "Avoid extremes in expression or position. " +
                            "Maintain measured and reasonable tone throughout.")
                }
                ResponseTone.INTENSE -> {
                    instructions.append("Use strong, powerful language. " +
                            "Express ideas with force and conviction. " +
                            "Choose words that convey energy and importance.")
                }
                ResponseTone.RESTRAINED -> {
                    instructions.append("Use controlled, reserved language. " +
                            "Hold back from full expression of emotion or opinion. " +
                            "Maintain deliberate moderation in tone and content.")
                }
                ResponseTone.BOLD -> {
                    instructions.append("Use confident language that takes risks. " +
                            "Express ideas with courage and originality. " +
                            "Avoid hedging or excessive qualification.")
                }
                ResponseTone.SUBTLE -> {
                    instructions.append("Use delicate, nuanced language. " +
                            "Convey meaning through suggestion and implication. " +
                            "Employ understatement and indirect expression.")
                }
                ResponseTone.DRAMATIC -> {
                    instructions.append("Use theatrical language emphasizing for effect. " +
                            "Heighten emotional impact through expressive devices. " +
                            "Create vivid impressions through dynamic language.")
                }
                ResponseTone.HISTORICAL -> {
                    instructions.append("Use language characteristic of a specific historical period or perspective. " +
                            "Incorporate period-appropriate references, idioms, and frameworks of understanding. " +
                            "Balance authentic historical voice with sufficient clarity for contemporary comprehension.")
                }
                ResponseTone.FUTURISTIC -> {
                    instructions.append("Use forward-looking language that anticipates technological and social evolution. " +
                            "Incorporate emerging concepts, terminology, and paradigm shifts not yet fully realized. " +
                            "Balance speculative elements with plausible extrapolation from current trends and scientific understanding.")
                }

                ResponseTone.CHILDLIKE -> {
                    instructions.append("Use accessible language with a sense of wonder and discovery. " +
                            "Break down complex concepts into intuitive, concrete examples and analogies. " +
                            "Maintain an enthusiastic, question-driven approach that invites curiosity and imagination.")
                }

                ResponseTone.STOIC -> {
                    instructions.append("Use reason-centered language focused on virtue, self-control, and acceptance. " +
                            "Distinguish between what can be changed and what must be accepted with equanimity. " +
                            "Draw on Stoic principles such as focusing on one's own actions rather than external outcomes.")
                }

                ResponseTone.SARCASTIC -> {
                    instructions.append("Use ironic statements that imply the opposite of their literal meaning for humorous effect. " +
                            "Create deliberate contrast between statement and context to highlight absurdities or contradictions. " +
                            "Use verbal cues like exaggeration or deadpan delivery to signal the ironic intent.")
                }

                ResponseTone.MENTORING -> {
                    instructions.append("Use guidance-oriented language that balances authority with empowerment. " +
                            "Share experiential wisdom through relevant examples and directed reflection questions. " +
                            "Scaffold understanding with appropriate challenges that promote growth within the zone of proximal development.")
                }

                ResponseTone.TACTICAL -> {
                    instructions.append("Use concrete, action-oriented language focused on execution and results. " +
                            "Organize information in priority sequence with clear decision points and contingencies. " +
                            "Emphasize resource allocation, timing considerations, and success metrics for practical implementation.")
                }

                ResponseTone.DIALECTICAL -> {
                    instructions.append("Use language that systematically explores contradictory perspectives to reach deeper truth. " +
                            "Present thesis and antithesis clearly before developing a synthesized understanding. " +
                            "Acknowledge tension between opposing views as productive rather than problematic.")
                }
                else -> { /* Default - no special instructions */ }
            }
            instructions.append("\n")
        }

        return instructions.toString().trim()
    }
}