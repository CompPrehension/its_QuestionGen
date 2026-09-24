package its.questions.gen.formulations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaseTest {

    /** Все принятые обозначения падежей - русские и латинские, полные и сокращённые - распознаются без учёта регистра. */
    @Test
    fun caseAliasesAreRecognized() {
        // Arrange.
        val aliases = mapOf(
            Case.Nom to listOf("и.п.", "им.п.", "и", "им", "и.", "им.", "n", "nom", "nom.", "И", "NOM"),
            Case.Gen to listOf("р.п.", "род.п.", "р", "род", "р.", "род.", "g", "gen", "gen.", "Р"),
            Case.Dat to listOf("д.п.", "дат.п.", "д", "дат", "д.", "дат.", "d", "dat", "dat."),
            Case.Acc to listOf("в.п.", "вин.п.", "в", "вин", "в.", "вин.", "a", "acc", "acc."),
            Case.Ins to listOf("т.п.", "тв.п.", "т", "тв", "т.", "тв.", "i", "ins", "ins."),
            Case.Pre to listOf("п.п.", "пр.п.", "п", "пр", "п.", "пр.", "p", "pre", "pre."),
        )

        // Act.
        val recognized = aliases.mapValues { (_, names) -> names.map { Case.fromString(it) }.toSet() }

        // Assert.
        assertEquals(aliases.mapValues { (case, _) -> setOf(case) }, recognized)
    }

    /** Неизвестное обозначение падежа не распознаётся. */
    @Test
    fun unknownCaseIsNull() {
        // Act & Assert.
        assertNull(Case.fromString("родительный"))
        assertNull(Case.fromString(""))
    }

    /** Порядок падежей соответствует номерам склонятора Padeg (именительный - первый). */
    @Test
    fun caseOrderMatchesDeclension() {
        // Act & Assert.
        assertEquals(listOf(Case.Nom, Case.Gen, Case.Dat, Case.Acc, Case.Ins, Case.Pre), Case.entries.toList())
    }
}
