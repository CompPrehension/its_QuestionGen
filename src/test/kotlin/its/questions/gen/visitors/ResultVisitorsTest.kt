package its.questions.gen.visitors

import its.model.nodes.BranchAggregationNode
import its.model.nodes.BranchResult.CORRECT
import its.model.nodes.BranchResult.ERROR
import its.model.nodes.BranchResult.NULL
import its.model.nodes.CycleAggregationNode
import its.model.nodes.ThoughtBranch
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.tree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Статический анализ возможных результатов ветвей: [GetPossibleResults] и [canHaveNullResult].
 */
class ResultVisitorsTest {

    /** Возможные результаты ветви - результаты всех её заключений. */
    @Test
    fun possibleResultsAreConclusions() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } };
                };
            }
        """)

        // Act.
        val results = GetPossibleResults().process(tree.mainBranch)

        // Assert.
        assertEquals(setOf(CORRECT, ERROR), results)
        assertFalse(tree.mainBranch.canHaveNullResult())
    }

    /** Заключение null делает NULL возможным результатом ветви. */
    @Test
    fun nullConclusionIsPossibleResult() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -> { conclude: null };
                    false -> { conclude: correct };
                };
            }
        """)

        // Act.
        val results = GetPossibleResults().process(tree.mainBranch)

        // Assert.
        assertEquals(setOf(CORRECT, NULL), results)
        assertTrue(tree.mainBranch.canHaveNullResult())
    }

    /** Исходы, для которых у узла агрегации нет перехода, становятся возможными результатами ветви. */
    @Test
    fun aggregationWithoutOutcomeGivesItsResult() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                agg and {
                    _ -> { conclude: correct };
                    _ -> { conclude: error };
                    correct -> { conclude: correct };
                };
            }
        """)

        // Act.
        val results = GetPossibleResults().process(tree.mainBranch)

        // Assert.
        assertEquals(setOf(CORRECT, ERROR, NULL), results)
    }

    /** Ветвь с агрегацией без перехода по null может завершиться с NULL, только если NULL могут дать все её ветви. */
    @Test
    fun branchAggregationNullNeedsAllBranchesNull() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.fragile) {
                    true -> {
                        agg and {
                            _ -[a1]-> { ask (X.weight > 3) { true -> { conclude: null }; false -> { conclude: correct }; } };
                            _ -[a2]-> { conclude: null };
                            correct -> { conclude: correct };
                            error -> { conclude: error };
                        } as allNull;
                    };
                    false -> {
                        agg and {
                            _ -> { conclude: null };
                            _ -> { conclude: correct };
                            correct -> { conclude: correct };
                            error -> { conclude: error };
                        } as someNull;
                    };
                };
            }
            meta for allNull [ alias = "allNull"; ]
            meta for someNull [ alias = "someNull"; ]
            meta for a1 [ alias = "a1"; ]
        """)

        // Act & Assert.
        assertTrue(tree.element<BranchAggregationNode>("allNull").canHaveNullResult())
        assertFalse(tree.element<BranchAggregationNode>("someNull").canHaveNullResult())
        assertTrue(tree.element<ThoughtBranch>("a1").canHaveNullResult())
        assertTrue(tree.mainBranch.canHaveNullResult())
    }

    /** Агрегация с явным переходом по null делегирует возможность NULL этому переходу. */
    @Test
    fun aggregationWithNullOutcomeDelegatesToIt() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                agg or {
                    _ -> { conclude: null };
                    _ -> { conclude: null };
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                    null -> { conclude: error };
                };
            }
        """)

        // Act & Assert.
        assertFalse(tree.mainBranch.canHaveNullResult())
        assertEquals(setOf(CORRECT, ERROR), GetPossibleResults().process(tree.mainBranch))
    }

    /** Циклическая агрегация всегда может дать NULL - объектов для перебора может не найтись. */
    @Test
    fun cycleAggregationCanAlwaysBeNull() {
        // Arrange.
        val tree = tree($$"""
            tpg T(X: item) {
                cycle and ($i.weight > 3) with item i {
                    _ -> { conclude: correct };
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                } as heavyOnes;
            }
            meta for heavyOnes [ alias = "heavyOnes"; ]
        """)

        // Act & Assert.
        assertTrue(tree.element<CycleAggregationNode>("heavyOnes").canHaveNullResult())
        assertTrue(tree.mainBranch.canHaveNullResult())
    }
}
