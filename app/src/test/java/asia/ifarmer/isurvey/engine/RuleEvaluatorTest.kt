package asia.ifarmer.isurvey.engine

import asia.ifarmer.isurvey.model.GroupJumpDto
import asia.ifarmer.isurvey.utils.Constants
import org.junit.Assert.*
import org.junit.Test

class RuleEvaluatorTest {

    @Test
    fun testParseQuestionOptions() {
        val commaSeparated = "Dhaka, Chittagong, Sylhet"
        val parsed1 = Constants.parseQuestionOptions(commaSeparated)
        assertEquals(listOf("Dhaka", "Chittagong", "Sylhet"), parsed1)

        val jsonArrayStr = "[\"Option A\", \"Option B\"]"
        val parsed2 = Constants.parseQuestionOptions(jsonArrayStr)
        assertEquals(listOf("Option A", "Option B"), parsed2)
    }

    @Test
    fun testEvaluateJumpTrigger() {
        val jumpGreater = GroupJumpDto(
            targetGroupId = 2,
            groupTitle = "Group 2",
            sourceQuestionId = 101,
            triggerValue = "50",
            triggerType = "greater"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpGreater, "75"))
        assertFalse(RuleEvaluator.evaluateJumpTrigger(jumpGreater, "30"))
        assertFalse(RuleEvaluator.evaluateJumpTrigger(jumpGreater, "50"))

        val jumpMatch = GroupJumpDto(
            targetGroupId = 3,
            groupTitle = "Group 3",
            sourceQuestionId = 102,
            triggerValue = "Yes, Definitely",
            triggerType = "match"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpMatch, "Yes"))
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpMatch, "definitely"))
        assertFalse(RuleEvaluator.evaluateJumpTrigger(jumpMatch, "No"))

        val jumpNotMatch = GroupJumpDto(
            targetGroupId = 4,
            groupTitle = "Group 4",
            sourceQuestionId = 103,
            triggerValue = "No",
            triggerType = "not_match"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpNotMatch, "Yes"))
        assertFalse(RuleEvaluator.evaluateJumpTrigger(jumpNotMatch, "No"))
    }

    @Test
    fun testEvaluateRuleStringOperators() {
        assertTrue(RuleEvaluator.evaluateRule(">= 10", "15"))
        assertTrue(RuleEvaluator.evaluateRule(">= 10", "10"))
        assertFalse(RuleEvaluator.evaluateRule(">= 10", "9"))

        assertTrue(RuleEvaluator.evaluateRule("< 5.5", "3.2"))
        assertFalse(RuleEvaluator.evaluateRule("< 5.5", "6"))

        assertTrue(RuleEvaluator.evaluateRule("!= No", "Yes"))
        assertFalse(RuleEvaluator.evaluateRule("!= No", "No"))

        assertTrue(RuleEvaluator.evaluateRule("NOT False", "True"))
        assertFalse(RuleEvaluator.evaluateRule("NOT False", "False"))

        assertTrue(RuleEvaluator.evaluateRule("Yes", "Yes"))
        assertTrue(RuleEvaluator.evaluateRule("Yes", "Yes, sure"))
        assertFalse(RuleEvaluator.evaluateRule("Yes", "No"))
    }
}
