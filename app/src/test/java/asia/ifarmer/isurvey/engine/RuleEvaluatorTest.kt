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

        val jsonObjectStr = "{\"opt1\": \"Dhaka\", \"opt2\": \"Sylhet\"}"
        val parsed3 = Constants.parseQuestionOptions(jsonObjectStr)
        assertTrue(parsed3.contains("Dhaka"))
        assertTrue(parsed3.contains("Sylhet"))
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

        val jumpElse = GroupJumpDto(
            targetGroupId = 5,
            groupTitle = "Group 5",
            sourceQuestionId = 104,
            triggerValue = "else",
            triggerType = "else"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpElse, "Anything"))
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

    @Test
    fun testEvaluateRuleCrossGroupValidationShapes() {
        // 1. Cross-group validation object with string validation
        val crossGroupStringRule = """
            {
                "validation_type": "match",
                "validation": "Dairy",
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(crossGroupStringRule, "Dairy"))
        assertFalse(RuleEvaluator.evaluateRule(crossGroupStringRule, "Fishery"))

        // 2. Cross-group validation object with map of matches
        val crossGroupMapRule = """
            {
                "validation_type": "match",
                "validation": {
                    "Dairy": true,
                    "Fishery": false
                },
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(crossGroupMapRule, "Dairy"))
        assertFalse(RuleEvaluator.evaluateRule(crossGroupMapRule, "Fishery"))
        assertFalse(RuleEvaluator.evaluateRule(crossGroupMapRule, "Agriculture"))

        // 3. Cross-group validation object with array of allowed options
        val crossGroupArrayRule = """
            {
                "validation_type": "match",
                "validation": ["Option A", "Option B"],
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(crossGroupArrayRule, "Option A"))
        assertTrue(RuleEvaluator.evaluateRule(crossGroupArrayRule, "Option B"))
        assertFalse(RuleEvaluator.evaluateRule(crossGroupArrayRule, "Option C"))

        // 4. Cross-group direct map without validation wrapper
        val directMapRule = """
            {
                "Dairy": true,
                "Fishery": false,
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(directMapRule, "Dairy"))
        assertFalse(RuleEvaluator.evaluateRule(directMapRule, "Fishery"))

        // 5. Cross-group numeric threshold rule
        val numericGreaterRule = """
            {
                "validation_type": "greater",
                "validation": 50,
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(numericGreaterRule, "75"))
        assertFalse(RuleEvaluator.evaluateRule(numericGreaterRule, "30"))
        assertFalse(RuleEvaluator.evaluateRule(numericGreaterRule, "50"))

        // 6. Cross-group not_match rule (decision else branch)
        val notMatchRule = """
            {
                "validation_type": "not_match",
                "validation": "Dairy",
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(notMatchRule, "Fishery"))
        assertFalse(RuleEvaluator.evaluateRule(notMatchRule, "Dairy"))
    }
}
