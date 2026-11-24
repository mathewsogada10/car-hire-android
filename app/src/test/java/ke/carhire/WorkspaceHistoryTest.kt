package ke.carhire

import ke.carhire.ui.workspace.WorkspaceHistory
import org.junit.Assert.*
import org.junit.Test

class WorkspaceHistoryTest {
    @Test
    fun backReturnsThroughEditorsAndVisitedSections() {
        val history = WorkspaceHistory()
        history.navigate("customers")
        val customerKey = history.current.key
        history.navigate("hires")
        val listKey = history.current.key
        history.edit()
        assertTrue(history.current.editor)
        history.navigate("expenses")
        history.close()
        assertTrue(history.current.editor)
        history.close()
        assertEquals(listKey, history.current.key)
        history.close()
        assertEquals(customerKey, history.current.key)
        history.close()
        assertEquals("home", history.current.page)
        history.close()
        assertEquals(1, history.entries.size)
    }

    @Test
    fun explicitHomeResetsHistoryAndClosingABackgroundSaveDoesNotPopTheCurrentPage() {
        val history = WorkspaceHistory()
        history.navigate("hires")
        history.edit()
        val editor = history.current.key
        history.navigate("payments")
        history.close(editor)
        assertEquals("payments", history.current.page)
        history.navigate("home")
        assertEquals(1, history.entries.size)
        assertEquals("home", history.current.page)
    }
}
