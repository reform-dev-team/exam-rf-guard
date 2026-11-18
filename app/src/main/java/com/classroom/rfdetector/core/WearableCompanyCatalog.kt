package com.classroom.rfdetector.core

/**
 * 2단계: 블루투스 회사 번호로 찾는 스마트 안경·헤드셋 후보.
 *
 * 1단계([WearableNameCatalog])는 기기가 알린 이름을 본다. 이름은 설정에서 바꾸거나 지울 수 있어서
 * 기본 이름을 그대로 둔 기기만 잡힌다. 회사 번호는 기기 프로그램이 신호에 넣는 값이라 설정으로 바꿀 수 없다.
 * 다만 회사 번호는 "그 회사가 만든 신호"라는 뜻일 뿐 제품을 증명하지 않는다. 그래서 화면에는 "후보"로만 쓴다.
 * 칩 회사 번호처럼 안경보다 다른 기기가 더 흔한 번호는 [WearableCompany.label]에 더 넓은 제목을 적는다.
 *
 * 이 목록은 화면에 붙이는 종류 이름만 바꾼다. 전파를 묶고 세는 기준([signalGroupKey])은 건드리지 않는다.
 *
 * 한 줄 추가하기: [entries]에 한 줄을 넣는다. 회사 이름은 app/src/main/assets/bluetooth-companies.tsv
 * (Bluetooth SIG 공식 등록부)의 이름과 한 글자도 다르면 안 되고, 다르면 WearableCompanyCatalogTest가 실패한다.
 * 다른 앱의 목록을 옮길 때 번호가 다른 회사 것인 경우가 있었으므로 이 검사는 빼지 않는다.
 */
data class WearableCompany(
    /** Bluetooth SIG 회사 번호 */
    val id: Int,
    /** 공식 등록부에 적힌 회사 이름 그대로 */
    val registryName: String,
    /** 이 번호로 찾으려는 제품 */
    val products: String,
    /** 같은 번호를 쓴다고 알려진, 안경·헤드셋이 아닌 기기. 알려진 것이 없으면 null */
    val sharedWith: String?,
    /** 실제 제품이 이 번호를 내보낸다고 적은 공개 자료. null이면 등록부에 회사만 있고 제품 확인 자료는 없다 */
    val reportedBy: String?,
    /** 목록에 뜨는 제목. 안 적으면 [WearableCompanyCatalog.LABEL]. 어떤 제목이든 "후보"로 끝나야 한다 */
    val label: String = WearableCompanyCatalog.LABEL,
)

object WearableCompanyCatalog {
    const val LABEL = "스마트 안경·헤드셋 후보"

    private const val NEARBY_GLASSES = "https://github.com/yjeanrenaud/yj_nearbyglasses"
    private const val NOPEEK = "https://github.com/getnopeek/nopeek-android"

    val entries = listOf(
        WearableCompany(0x0D53, "Luxottica Group S.p.A", "Ray-Ban Meta·Oakley Meta 안경", null, "$NEARBY_GLASSES · $NOPEEK"),
        WearableCompany(0x01AB, "Meta Platforms, Inc.", "Meta 안경·Quest 헤드셋", "Portal 화상통화 기기", "$NEARBY_GLASSES · $NOPEEK"),
        WearableCompany(0x058E, "Meta Platforms Technologies, LLC", "Meta 안경·Quest 헤드셋", null, "$NEARBY_GLASSES · $NOPEEK"),
        WearableCompany(0x03C2, "Snapchat Inc", "Snap Spectacles 안경", null, "$NEARBY_GLASSES · $NOPEEK"),
        WearableCompany(0x05D6, "Zhuhai Jieli technology Co.,Ltd", "값싼 카메라 안경(Rogbird VisionPro·Rollme VistaView 등)", "이 회사 칩을 쓰는 값싼 이어폰·스피커", NEARBY_GLASSES, label = "무선 이어폰·스마트 안경 후보"),
        WearableCompany(0x060C, "Vuzix Corporation", "Vuzix 안경", null, null),
        WearableCompany(0x10F9, "Even Realities Ltd.", "Even Realities 안경", null, null),
        WearableCompany(0x055D, "Valve Corporation", "Valve Index 헤드셋", null, null),
        WearableCompany(0x02ED, "HTC Corporation", "HTC Vive 헤드셋", null, null),
    )

    private val byId = entries.associateBy { it.id }

    /** 신호에 실린 회사 번호 중 목록에 있는 것. 여러 개면 번호가 작은 것. */
    fun match(manufacturerIds: Set<Int>): WearableCompany? = manufacturerIds.sorted().firstNotNullOfOrNull { byId[it] }
}
