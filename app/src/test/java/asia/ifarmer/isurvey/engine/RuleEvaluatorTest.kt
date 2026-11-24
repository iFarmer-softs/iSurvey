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

        // Flowchart decision else handle without explicit triggerType="else"
        val jumpDecisionElseHandle = GroupJumpDto(
            targetGroupId = 6,
            groupTitle = "Group 6",
            sourceQuestionId = 105,
            triggerValue = "Dairy",
            sourceHandle = "else"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpDecisionElseHandle, "Fishery"))
        assertFalse(RuleEvaluator.evaluateJumpTrigger(jumpDecisionElseHandle, "Dairy"))

        val jumpDecisionElseWithoutVal = GroupJumpDto(
            targetGroupId = 7,
            groupTitle = "Group 7",
            sourceQuestionId = 106,
            sourceHandle = "else"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpDecisionElseWithoutVal, "AnyAnswer"))
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

    @Test
    fun testParserGroupJumpsCompatibility() {
        val jsonSnakeCase = """
            {
                "total_groups": 3,
                "current_group": 1,
                "groups_to_jump": [
                    {
                        "group_id": "2",
                        "group_title": "Group 2",
                        "source_question_id": 101,
                        "trigger_value": "Dairy",
                        "trigger_type": "match"
                    },
                    {
                        "group_id": "3",
                        "group_title": "Group 3",
                        "source_question_id": 101,
                        "source_handle": "else"
                    }
                ],
                "data": []
            }
        """.trimIndent()
        val parserSnake = Parser(jsonSnakeCase)
        assertEquals(3, parserSnake.totalGroups)
        assertEquals(1, parserSnake.currentGroup)
        val jumpsSnake = parserSnake.getGroupsToJump()
        assertEquals(2, jumpsSnake.size)
        assertEquals(2, jumpsSnake[0].targetGroupId)
        assertEquals("Group 2", jumpsSnake[0].groupTitle)
        assertEquals(101, jumpsSnake[0].sourceQuestionId)
        assertEquals(3, jumpsSnake[1].targetGroupId)
        assertEquals("else", jumpsSnake[1].sourceHandle)

        val jsonCamelCase = """
            {
                "totalGroups": 4,
                "currentGroup": 2,
                "groupsToJump": [
                    {
                        "groupId": "3",
                        "groupTitle": "Group 3",
                        "sourceQuestionId": 201,
                        "triggerValue": "Yes"
                    }
                ],
                "data": []
            }
        """.trimIndent()
        val parserCamel = Parser(jsonCamelCase)
        assertEquals(4, parserCamel.totalGroups)
        assertEquals(2, parserCamel.currentGroup)
        val jumpsCamel = parserCamel.getGroupsToJump()
        assertEquals(1, jumpsCamel.size)
        assertEquals(3, jumpsCamel[0].targetGroupId)
        assertEquals("Group 3", jumpsCamel[0].groupTitle)
        assertEquals(201, jumpsCamel[0].sourceQuestionId)
    }

    @Test
    fun testPhoneValidation() {
        // Valid 11-digit BD mobile numbers
        assertTrue(Constants.isValidPhoneNumber("01712345678"))
        assertTrue(Constants.isValidPhoneNumber("01812345678"))
        assertTrue(Constants.isValidPhoneNumber("01912345678"))
        assertTrue(Constants.isValidPhoneNumber("01612345678"))
        assertTrue(Constants.isValidPhoneNumber("01512345678"))
        assertTrue(Constants.isValidPhoneNumber("01312345678"))
        assertTrue(Constants.isValidPhoneNumber("01412345678"))
        assertTrue(Constants.isValidPhoneNumber("01112345678"))

        // Valid with country code prefix
        assertTrue(Constants.isValidPhoneNumber("+8801712345678"))
        assertTrue(Constants.isValidPhoneNumber("8801712345678"))
        assertTrue(Constants.isValidPhoneNumber("008801712345678"))

        // Valid 10 digits without leading 0 (as typed inside edittext after +880)
        assertTrue(Constants.isValidPhoneNumber("1712345678"))
        assertTrue(Constants.isValidPhoneNumber("1812345678"))

        // Valid with spaces and dashes
        assertTrue(Constants.isValidPhoneNumber("01712-345678"))
        assertTrue(Constants.isValidPhoneNumber("01712 345678"))
        assertTrue(Constants.isValidPhoneNumber("+880 1712-345678"))

        // Invalid phone numbers
        assertFalse(Constants.isValidPhoneNumber("0171234567"))    // 10 digits starting with 0 (too short)
        assertFalse(Constants.isValidPhoneNumber("017123456789"))  // 12 digits (too long)
        assertFalse(Constants.isValidPhoneNumber("171234567"))     // 9 digits (too short)
        assertFalse(Constants.isValidPhoneNumber("02712345678"))   // Invalid operator (02)
        assertFalse(Constants.isValidPhoneNumber("00123456789"))   // Invalid prefix
        assertFalse(Constants.isValidPhoneNumber("01012345678"))   // Invalid operator (010)
        assertFalse(Constants.isValidPhoneNumber("12345"))         // Too short
        assertFalse(Constants.isValidPhoneNumber("abcdefghijk"))   // Non-numeric
        assertFalse(Constants.isValidPhoneNumber(""))              // Empty
        assertFalse(Constants.isValidPhoneNumber(null))            // Null
    }

    @Test
    fun testNumberValidation() {
        // Integer
        assertTrue(Constants.isValidInteger("123"))
        assertTrue(Constants.isValidInteger("-123"))
        assertTrue(Constants.isValidInteger("0"))
        assertTrue(Constants.isValidInteger("1,000,000"))
        assertFalse(Constants.isValidInteger("12.5"))
        assertFalse(Constants.isValidInteger("abc"))
        assertFalse(Constants.isValidInteger(""))
        assertFalse(Constants.isValidInteger(null))

        // Float
        assertTrue(Constants.isValidFloat("123"))
        assertTrue(Constants.isValidFloat("123.45"))
        assertTrue(Constants.isValidFloat("-123.45"))
        assertTrue(Constants.isValidFloat("0.0"))
        assertTrue(Constants.isValidFloat(".5"))
        assertTrue(Constants.isValidFloat("1,234.56"))
        assertFalse(Constants.isValidFloat("abc"))
        assertFalse(Constants.isValidFloat(""))
        assertFalse(Constants.isValidFloat(null))
    }

    @Test
    fun testDateValidation() {
        assertTrue(Constants.isValidDate("2024-05-15"))
        assertTrue(Constants.isValidDate("2026-11-24"))
        assertTrue(Constants.isValidDate("2000-01-01"))
        assertFalse(Constants.isValidDate("2024-13-01")) // Month > 12
        assertFalse(Constants.isValidDate("2024-05-32")) // Day > 31
        assertFalse(Constants.isValidDate("2024/05/15")) // Wrong separator
        assertFalse(Constants.isValidDate("15-05-2024")) // Wrong format
        assertFalse(Constants.isValidDate("invalid"))
        assertFalse(Constants.isValidDate(""))
        assertFalse(Constants.isValidDate(null))
    }

    @Test
    fun testCrossGroupPhoneEvaluation() {
        // User entered phone number in Group 1 is stored with 0 prefix: "01712345678"
        val phoneAnswer = "01712345678"

        // 1. Raw string targets with various formats
        assertTrue(RuleEvaluator.evaluateRule("+8801712345678", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("8801712345678", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("1712345678", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("01712345678", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("+880 1712-345678", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("+88017", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("017", phoneAnswer))
        assertFalse(RuleEvaluator.evaluateRule("+8801812345678", phoneAnswer))

        // 2. Cross-group validation JSON with map
        val phoneMapRuleIntl = """
            {
                "validation_type": "match",
                "validation": {
                    "+8801712345678": true
                },
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(phoneMapRuleIntl, phoneAnswer))

        val phoneMapRule10Digit = """
            {
                "validation_type": "match",
                "validation": {
                    "1712345678": true
                },
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(phoneMapRule10Digit, phoneAnswer))

        // 3. Wildcard / Non-empty presence checks
        val wildcardRule = """
            {
                "validation_type": "match",
                "validation": {
                    "": true
                },
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(wildcardRule, phoneAnswer))

        val notMatchEmptyRule = """
            {
                "validation_type": "not_match",
                "validation": "",
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(notMatchEmptyRule, phoneAnswer))

        // 4. Boolean true rule (e.g. dependency satisfied when question answered)
        assertTrue(RuleEvaluator.evaluateRule(true, phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("true", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("*", phoneAnswer))

        // 5. Jump trigger based on phone input
        val jumpPhone = GroupJumpDto(
            targetGroupId = 2,
            groupTitle = "Group 2",
            sourceQuestionId = 101,
            triggerValue = "+8801712345678",
            triggerType = "match"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpPhone, phoneAnswer))

        val jumpPhoneElse = GroupJumpDto(
            targetGroupId = 3,
            groupTitle = "Group 3",
            sourceQuestionId = 101,
            triggerValue = "+8801712345678",
            triggerType = "not_match"
        )
        assertFalse(RuleEvaluator.evaluateJumpTrigger(jumpPhoneElse, phoneAnswer))
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpPhoneElse, "01812345678"))
    }

    @Test
    fun testCrossGroupEdgeLabelValidation() {
        val phoneAnswer = "01712345678"

        // 1. Cross-group edge label as string validation target
        assertTrue(RuleEvaluator.evaluateRule("Cross-Group: G1 ➔ G2", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("Cross-Group", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("cross_group", phoneAnswer))
        assertTrue(RuleEvaluator.evaluateRule("default-next", phoneAnswer))
        assertFalse(RuleEvaluator.evaluateRule("Cross-Group: G1 ➔ G2", ""))
        assertFalse(RuleEvaluator.evaluateRule("Cross-Group: G1 ➔ G2", null))

        // 2. Cross-group edge label inside JSON config with string validation
        val crossGroupStringRule = """
            {
                "validation_type": "match",
                "validation": "Cross-Group: G1 ➔ G2",
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(crossGroupStringRule, phoneAnswer))
        assertFalse(RuleEvaluator.evaluateRule(crossGroupStringRule, ""))

        // 3. Cross-group edge label inside JSON config with map validation
        val crossGroupMapRule = """
            {
                "validation_type": "match",
                "validation": {
                    "Cross-Group: G1 ➔ G2": true
                },
                "is_same_group": false,
                "group": 1
            }
        """.trimIndent()
        assertTrue(RuleEvaluator.evaluateRule(crossGroupMapRule, phoneAnswer))
        assertFalse(RuleEvaluator.evaluateRule(crossGroupMapRule, ""))

        // 4. GroupJump with cross-group label or sourceHandle
        val jumpCrossGroup = GroupJumpDto(
            targetGroupId = 2,
            groupTitle = "Group 2",
            sourceQuestionId = 101,
            triggerValue = "Cross-Group: G1 ➔ G2",
            triggerType = "match"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpCrossGroup, phoneAnswer))

        val jumpCrossGroupHandle = GroupJumpDto(
            targetGroupId = 2,
            groupTitle = "Group 2",
            sourceQuestionId = 101,
            sourceHandle = "Cross-Group: G1 ➔ G2"
        )
        assertTrue(RuleEvaluator.evaluateJumpTrigger(jumpCrossGroupHandle, phoneAnswer))
    }
}
