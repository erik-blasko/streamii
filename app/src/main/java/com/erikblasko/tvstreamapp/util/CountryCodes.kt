package com.erikblasko.tvstreamapp.util

import com.erikblasko.tvstreamapp.data.model.Channel
import java.util.Locale

// English country names → ISO code (for detecting countries in group-title)
val englishCountryToCode = mapOf(
    "afghanistan" to "AF", "albania" to "AL", "algeria" to "DZ", "andorra" to "AD",
    "angola" to "AO", "argentina" to "AR", "armenia" to "AM", "australia" to "AU",
    "austria" to "AT", "azerbaijan" to "AZ", "bahamas" to "BS", "bahrain" to "BH",
    "bangladesh" to "BD", "barbados" to "BB", "belarus" to "BY", "belgium" to "BE",
    "belize" to "BZ", "benin" to "BJ", "bhutan" to "BT", "bolivia" to "BO",
    "bosnia" to "BA", "bosnia and herzegovina" to "BA", "botswana" to "BW",
    "brazil" to "BR", "brunei" to "BN", "bulgaria" to "BG", "burkina faso" to "BF",
    "burundi" to "BI", "cambodia" to "KH", "cameroon" to "CM", "canada" to "CA",
    "chad" to "TD", "chile" to "CL", "china" to "CN", "colombia" to "CO",
    "congo" to "CD", "costa rica" to "CR", "croatia" to "HR", "cuba" to "CU",
    "curacao" to "CW", "cyprus" to "CY", "czech republic" to "CZ", "czechia" to "CZ",
    "denmark" to "DK", "dominica" to "DM", "dominican republic" to "DO",
    "ecuador" to "EC", "egypt" to "EG", "el salvador" to "SV", "eritrea" to "ER",
    "estonia" to "EE", "ethiopia" to "ET", "fiji" to "FJ", "finland" to "FI",
    "france" to "FR", "gabon" to "GA", "gambia" to "GM", "georgia" to "GE",
    "germany" to "DE", "ghana" to "GH", "greece" to "GR", "grenada" to "GD",
    "guatemala" to "GT", "guinea" to "GN", "guyana" to "GY", "haiti" to "HT",
    "honduras" to "HN", "hong kong" to "HK", "hungary" to "HU", "iceland" to "IS",
    "india" to "IN", "indonesia" to "ID", "iran" to "IR", "iraq" to "IQ",
    "ireland" to "IE", "israel" to "IL", "italy" to "IT", "jamaica" to "JM",
    "japan" to "JP", "jordan" to "JO", "kazakhstan" to "KZ", "kenya" to "KE",
    "kosovo" to "XK", "kuwait" to "KW", "kyrgyzstan" to "KG", "laos" to "LA",
    "latvia" to "LV", "lebanon" to "LB", "lesotho" to "LS", "liberia" to "LR",
    "libya" to "LY", "liechtenstein" to "LI", "lithuania" to "LT", "luxembourg" to "LU",
    "macao" to "MO", "macau" to "MO", "madagascar" to "MG", "malawi" to "MW",
    "malaysia" to "MY", "maldives" to "MV", "mali" to "ML", "malta" to "MT",
    "mauritania" to "MR", "mauritius" to "MU", "mexico" to "MX", "moldova" to "MD",
    "monaco" to "MC", "mongolia" to "MN", "montenegro" to "ME", "morocco" to "MA",
    "mozambique" to "MZ", "myanmar" to "MM", "namibia" to "NA", "nepal" to "NP",
    "netherlands" to "NL", "new zealand" to "NZ", "nicaragua" to "NI", "niger" to "NE",
    "nigeria" to "NG", "north korea" to "KP", "north macedonia" to "MK", "norway" to "NO",
    "oman" to "OM", "pakistan" to "PK", "palestine" to "PS", "panama" to "PA",
    "papua new guinea" to "PG", "paraguay" to "PY", "peru" to "PE",
    "philippines" to "PH", "poland" to "PL", "portugal" to "PT", "puerto rico" to "PR",
    "qatar" to "QA", "romania" to "RO", "russia" to "RU", "rwanda" to "RW",
    "saudi arabia" to "SA", "senegal" to "SN", "serbia" to "RS", "seychelles" to "SC",
    "sierra leone" to "SL", "singapore" to "SG", "slovakia" to "SK", "slovenia" to "SI",
    "somalia" to "SO", "south africa" to "ZA", "south korea" to "KR",
    "south sudan" to "SS", "spain" to "ES", "sri lanka" to "LK", "sudan" to "SD",
    "suriname" to "SR", "sweden" to "SE", "switzerland" to "CH", "syria" to "SY",
    "taiwan" to "TW", "tajikistan" to "TJ", "tanzania" to "TZ", "thailand" to "TH",
    "togo" to "TG", "tonga" to "TO", "trinidad and tobago" to "TT", "tunisia" to "TN",
    "turkey" to "TR", "turkiye" to "TR", "turkmenistan" to "TM", "uganda" to "UG",
    "ukraine" to "UA", "united arab emirates" to "AE", "united kingdom" to "GB",
    "united states" to "US", "united states of america" to "US", "usa" to "US",
    "uruguay" to "UY", "uzbekistan" to "UZ", "vatican" to "VA", "venezuela" to "VE",
    "vietnam" to "VN", "yemen" to "YE", "zambia" to "ZM", "zimbabwe" to "ZW",
)

/** Extra names some playlists use as group-title, beside the English country names */
private val countryAliases = mapOf(
    "uk" to "GB", "great britain" to "GB", "england" to "GB", "czech" to "CZ",
    "korea" to "KR", "russian federation" to "RU", "viet nam" to "VN",
    "trinidad" to "TT", "faroe islands" to "FO", "greenland" to "GL", "san marino" to "SM"
)

fun countryCodeFromName(name: String): String? {
    val key = name.lowercase().trim()
    return englishCountryToCode[key] ?: countryAliases[key]
}

/** iptv-org uses "UK" for the United Kingdom; the ISO code (and the flag) is "GB". */
fun normalizeCountryCode(code: String): String {
    val upper = code.uppercase()
    return when {
        upper == "UK" -> "GB"
        upper.length == 2 && upper.all { it in 'A'..'Z' } -> upper
        else -> Channel.UNKNOWN_COUNTRY
    }
}

fun countryFlag(code: String): String {
    if (code.length != 2 || code == Channel.UNKNOWN_COUNTRY) return ""
    val upper = code.uppercase()
    val first = Character.toChars(0x1F1E6 - 'A'.code + upper[0].code)
    val second = Character.toChars(0x1F1E6 - 'A'.code + upper[1].code)
    return String(first) + String(second)
}

/**
 * Country name in the app language, from the system's own translations
 * ("SK" -> "Slovensko" / "Slovakia"). Null for unknown codes.
 */
fun countryDisplayName(code: String, locale: Locale): String? {
    if (code == Channel.UNKNOWN_COUNTRY) return null
    val name = Locale("", code).getDisplayCountry(locale)
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
}
