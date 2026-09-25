package com.example.data

enum class TestType {
    CIRCLED_ON_SHEET, // HS khoanh tròn đáp án trên đề
    ANSWER_TABLE      // HS điền đáp án vào bảng/ô đáp án
}

data class StudentAnswerDetail(
    val questionNumber: Int,
    val studentAnswer: String, // e.g. "A", "B", "C", "D", "Trống", "Nhiều đáp án"
    val correctAnswer: String,
    val isCorrect: Boolean,
    val points: Double,
    val isGraded: Boolean // Whether this question was selected for grading
)

data class GradingResult(
    val id: Int = 0,
    val studentName: String,
    val className: String = "",
    val examName: String = "",
    val score: Double,
    val maxScore: Double,
    val totalQuestions: Int,
    val gradedCount: Int,
    val correctCount: Int,
    val testType: TestType,
    val answers: List<StudentAnswerDetail>,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null
)

data class ExamConfig(
    val id: Int = 0,
    val examName: String = "Bài Kiểm Tra 1",
    val className: String = "12A1",
    val testType: TestType = TestType.ANSWER_TABLE,
    val totalQuestions: Int = 20,
    val pointsPerCorrect: Double = 0.5,
    val masterKeys: Map<Int, String> = emptyMap(), // Map<QuestionNumber, CorrectOption>
    val selectedQuestions: Set<Int> = emptySet(), // If empty, all are graded
    val totalPages: Int = 1
) {
    fun isQuestionGraded(num: Int): Boolean {
        return selectedQuestions.isEmpty() || selectedQuestions.contains(num)
    }

    fun calculateScore(studentAnswers: Map<Int, String>): Double {
        var score = 0.0
        for ((num, correctAns) in masterKeys) {
            if (isQuestionGraded(num)) {
                val studentAns = studentAnswers[num]?.trim()?.uppercase() ?: ""
                val cleanAns = when {
                    studentAns == "X" || studentAns.contains("NHIỀU") || studentAns.length > 1 -> "X"
                    studentAns.isEmpty() || studentAns == "TRỐNG" || studentAns == "-" -> "-"
                    else -> studentAns
                }
                if (cleanAns in listOf("A", "B", "C", "D") && cleanAns == correctAns.trim().uppercase()) {
                    score += pointsPerCorrect
                }
            }
        }
        return score
    }
}

object AnswerKeyParser {
    /**
     * Parses flexible text input (CSV, lines, lists) into a map of question number to answer.
     * Supports formats like:
     * - 1,A
     * - 2;B
     * - 3. C
     * - 4: D
     * - A B C D (separated by space or tabs)
     */
    fun parse(text: String): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        if (text.isBlank()) return map

        val lines = text.lines()
        var questionCounter = 1

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Check if line matches "number delimiter answer" e.g. "1,A" or "1.A" or "1: A" or "Câu 1: A"
            val cleanedLine = line.replace("Câu", "", ignoreCase = true).trim()
            
            // Try matching patterns like "1, A" or "1. A" or "1: A" or "1 - A"
            val regexSeparators = Regex("^[\\s]*(\\d+)[\\s]*[,\\.\\-\\:\\t\\s]+[\\s]*([a-zA-Z\\?]+)[\\s]*$")
            val match = regexSeparators.find(cleanedLine)
            
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull()
                val ans = match.groupValues[2].uppercase().trim()
                if (num != null && ans.isNotEmpty()) {
                    map[num] = ans
                    continue
                }
            }

            // Fallback: If the line is just a single character option (A, B, C, D) or comma-separated options
            // e.g. a line containing "A" gets assigned to sequential questionCounter
            if (line.length == 1 && line[0].isLetter()) {
                map[questionCounter++] = line.uppercase()
                continue
            }

            // Fallback 2: Check if multiple answers are written on one line separated by spaces or commas
            // e.g. "A, B, C, D" or "A B C D" or "1.A, 2.B, 3.C"
            val parts = line.split(Regex("[,;\\s\\t]+"))
            var hasAssignedSequence = false
            for (part in parts) {
                val trimmedPart = part.trim()
                if (trimmedPart.isEmpty()) continue
                
                // Check if part is like "1.A"
                val partMatch = Regex("^(\\d+)[\\.\\:\\-]*( [a-zA-Z]|[a-zA-Z])$").find(trimmedPart)
                if (partMatch != null) {
                    val num = partMatch.groupValues[1].toIntOrNull()
                    val ans = partMatch.groupValues[2].uppercase().trim()
                    if (num != null && ans.isNotEmpty()) {
                        map[num] = ans
                        hasAssignedSequence = true
                    }
                } else if (trimmedPart.length == 1 && trimmedPart[0].isLetter()) {
                    // Just letters in sequence
                    map[questionCounter++] = trimmedPart.uppercase()
                    hasAssignedSequence = true
                }
            }
            if (hasAssignedSequence) continue
        }
        return map
    }

    /**
     * Formats a master key map into a clean standard CSV string.
     */
    fun formatToCsv(masterKeys: Map<Int, String>): String {
        return masterKeys.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key},${it.value}" }
    }
}
