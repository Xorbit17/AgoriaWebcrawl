import io.github.bonigarcia.wdm.WebDriverManager
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.util.function.Predicate
import kotlin.NoSuchElementException

val URL = "https://tools.agoria.be/WWW.wsc/rep/prg/ApplLookup?HLproc=webextra/prg/olEntityList"
val postCodeRegex = Regex("[0-9]{4}")
val telephoneRegex = Regex("(((\\+[0-9]{1,2}|00[0-9]{1,2})[-\\ .]?)?)(\\d[-\\ .]?){5,15}")
val maxWaitTime = 10

fun main(args: Array<String>) {
    val maxPages = if (args.size > 1) args[1].toInt() else 10000

    println("Setup webdrivermanager")
    WebDriverManager.chromedriver().setup()
    val options = ChromeOptions()
    options.addArguments("--headless")
    val driver = ChromeDriver(options)
    println("Driver setup. Getting $URL.")
    driver.get(URL)
    val results = ArrayList<Contact>()
    var lastNumExtracted = 20
    var pageNum = 0
    try {
        while (lastNumExtracted >= 20 && pageNum < maxPages) {
            var resultsOfPage = extractFromPage(driver)
            results.addAll(resultsOfPage)
            println("Page ${pageNum++}: results ${resultsOfPage.size}")
            lastNumExtracted = resultsOfPage.size
            val navNext =
                driver.findElement(By.xpath("/html/body/div[1]/div/article/form[2]/div[1]/div/nav/ul/li[3]/a"))
            navNext.click()
            waitUntilFormReady(driver)
        }
    } catch (e: WebDriverException) {
        e.printStackTrace()
    }
    println("Ending session")
    driver.close()
    println("Exporting result. (${results.size} contacts)")
    val writer = ExcelWriter(args[0])
    writer.writeContacts(results)
    writer.save()
    writer.close()
    print("Exported to ${args[0]}")
}

fun waitUntilFormReady(driver: ChromeDriver) {
    /*
    fun linkBad():Boolean {
        try {
            driver.findElement(By.name("frmEntityList"))
            return false
        } catch (e: StaleElementReferenceException) {
            return true
        }
    }
    val form = driver.findElement(By.name("frmEntityList"))
     */
    WebDriverWait(driver, Duration.ofSeconds(maxWaitTime.toLong())).until { driver ->
        (driver as JavascriptExecutor).executeScript("return document.readyState").equals("complete")
    }
    Thread.sleep(1000L)
}

fun extractFromPage(driver: ChromeDriver): List<Contact> {
    val results = ArrayList<Contact>()
    var form: WebElement? = null
    var retries = 0
    while ((form == null) && (retries < 2)) {
        try {
            form = driver.findElement(By.name("frmEntityList"))
        } catch (e: org.openqa.selenium.NoSuchElementException) {
            println("Retry fetching form ($retries)")
            Thread.sleep(1000)
            form = null
            retries++
        }
    }

    val rowElements = form!!.findElements(By.className("row"))
    rowElements.subList(1, rowElements.size - 1).forEach { element ->
        val cols = element.findElement(By.className("form-group")).findElements(By.tagName("div"))
        val informationCol = cols.find { it.getAttribute("class")=="col-sm-6"}!!
        val result = Contact.parse(informationCol.text)
        val websiteCol = cols.find { it.getAttribute("class")=="col-sm-6 text-right"}!!
        try {
            result.website = websiteCol.findElement(By.tagName("a")).getAttribute("href")
        } catch (e:Exception) {
            //Pass
        }
        results.add(result)
    }
    return results
}


class ExcelWriter(val fileName: String) {

    val workBook: XSSFWorkbook = XSSFWorkbook()

    val workSheet: XSSFSheet = workBook.createSheet()

    init {
        val firstRow = workSheet.createRow(0)
        var cellIndex = 0
        Contact.getHeaders().forEach { header ->
            val cell = firstRow.createCell(cellIndex++)
            cell.setCellValue(header)
        }
        //workbook is ready

    }

    fun writeContacts(contacts: List<Contact>, rowNr: Int = 1) {
        var rowCursor = rowNr
        contacts.forEach { contact ->
            val row = workSheet.createRow(rowCursor++)
            var cellIndex = 0
            contact.toList().forEach { item ->
                val cell = row.createCell(cellIndex++)
                cell.setCellValue(item)
            }
        }
    }

    fun save() {
        val fileStream = FileOutputStream(File(fileName))
        workBook.write(fileStream)
        fileStream.close()
    }

    fun close() {
        workBook.close()
    }
}

enum class Province(val postCodeIntervals: List<IntRange>, val excelName: String) {
    BRUSSEL_CAP(listOf(1000..1299), "Brussels Hoofdstedelijk gewest"),
    WAALS_BRABANT(listOf(1300..1499), "Brussels Hoofdstedelijk gewest"),
    VLAAMS_BRABANT(listOf(1500..1999, 3000..3499), "Vlaams Brabant"),
    ANTWERPEN(listOf(2000..2999), "Vlaams Brabant"),
    LIMBURG(listOf(3500..3999), "Limburg"),
    LUIK(listOf(4000..4999), "Luik"),
    NAMEN(listOf(5000..5999), "Namen"),
    HENEGOUWEN(listOf(6000..6599, 7000..7999), "Henegouwen"),
    LUXEMBURG(listOf(6600..6999), "Luxemburg"),
    WEST_VLAANDEREN(listOf(8000..8999), "West Vlaanderen"),
    OOST_VLAANDEREN(listOf(9000..9999), "Oost Vlaanderen");

    companion object {
        fun fromPostCode(postCode: Int): Province? {
            return (Province.values().find { province ->
                province.postCodeIntervals.any { interval ->
                    interval.contains(postCode)
                }
            })
        }
    }
}

enum class Region(val provinces: List<Province>, val excelName: String) {
    VLAANDEREN(
        listOf(
            Province.VLAAMS_BRABANT,
            Province.ANTWERPEN,
            Province.LIMBURG,
            Province.WEST_VLAANDEREN,
            Province.OOST_VLAANDEREN
        ), "Vlaanderen"
    ),
    WALLONIE(
        listOf(Province.WAALS_BRABANT, Province.LUIK, Province.NAMEN, Province.HENEGOUWEN, Province.LUXEMBURG),
        "Wallonie"
    ),
    BRUSSEL(listOf(Province.BRUSSEL_CAP), "Brussel");

    companion object {
        fun fromProvince(province: Province): Region? {
            return (Region.values().find { region -> region.provinces.contains(province) })

        }

        fun fromPostCode(postCode: Int): Region? {
            val province = Province.fromPostCode(postCode)
            return if (province == null) {
                null
            } else {
                fromProvince(province)
            }
        }
    }
}

data class Address(
    val streetAndNumber: String,
    val postCode: Int,
    val city: String,
    val province: Province?,
    val region: Region?,
) {
    fun toStringList(): List<String?> {
        return listOf(
            streetAndNumber,
            postCode.toString(),
            city,
            province?.excelName ?: "Nvt",
            region?.excelName ?: "Nvt"
        )
    }

    companion object {
        fun getHeaders(): List<String> {
            return listOf("Straat en nummer", "postcode", "Stad", "Provincie", "Regio")
        }


        fun parse(argString: String): Address {
            val parts = argString.split(",")
            //Somme street and number are sure to be in the first parts
            var streetAndNumber = parts[0].trim()
            //Postcode is sure to be in the final part
            val lastPart = parts.last().trim()
            val matchResult = postCodeRegex.find(lastPart)
            var postCode = 0
            var city = "Niet gevonden"
            if (matchResult != null) {
                postCode = matchResult.value.toInt()
                city = lastPart.removeRange(matchResult.range).trim()
            }
            //Dump other parts
            if (parts.size > 2) {
                streetAndNumber =
                    (streetAndNumber + " " + (parts.subList(1, parts.size - 1).joinToString { "$it " })).trim()
            }
            return Address(
                streetAndNumber,
                postCode,
                city,
                Province.fromPostCode(postCode),
                Region.fromPostCode(postCode)
            )
        }
    }
}

data class Contact(
    var name: String,
    var address: Address,
    var telephone: String? = null,
    var contactName: String? = null,
    var website: String? = null,
) {
    override fun toString(): String {
        return "Company:$name"
    }

    fun toList(): List<String?> {
        val result = ArrayList<String?>()
        result.add(name)
        result.addAll(address.toStringList())
        result.addAll(listOf(telephone, contactName, website))
        return result
    }

    companion object {
        fun getHeaders(): List<String> {
            val headers = arrayListOf("Bedrijfsnaam")
            headers.addAll(Address.getHeaders())
            headers.addAll(listOf("Telefoon", "Contact naam", "Website"))
            return headers
        }

        fun parse(argString: String): Contact {
            val parts = argString.split("\n")
            val name = parts[0].trim()
            val address = Address.parse(parts[1])
            val telephone = if (parts.size > 2) parts[2] else null
            val contactName = if (parts.size > 3) parts[3] else null
            return Contact(name, address, telephone, contactName, null)
        }

        fun parseWithWebsite(argString: String, website: String): Contact {
            val result = Contact.parse(argString)
            result.website = website
            return result
        }
    }
}


