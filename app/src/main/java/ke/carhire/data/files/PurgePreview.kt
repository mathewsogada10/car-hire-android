package ke.carhire.data.files

data class PurgePreview(
    val exportId: String,
    val through: String,
    val ids: List<String>,
    val hash: String,
    val excluded: Int,
)
