package ke.carhire.ui.editor

data class Field(
    val key: String,
    val label: String,
    val type: String = "text",
    val options: List<String> = emptyList(),
    val reference: String = "",
    val optional: Boolean = false,
)
