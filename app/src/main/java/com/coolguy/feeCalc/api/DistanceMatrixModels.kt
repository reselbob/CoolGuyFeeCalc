package com.coolguy.feeCalc.api

import com.google.gson.annotations.SerializedName

data class DistanceMatrixResponse(
    @SerializedName("status") val status: String,
    @SerializedName("origin_addresses") val originAddresses: List<String>,
    @SerializedName("destination_addresses") val destinationAddresses: List<String>,
    @SerializedName("rows") val rows: List<Row>
)

data class Row(
    @SerializedName("elements") val elements: List<Element>
)

data class Element(
    @SerializedName("status") val status: String,
    @SerializedName("distance") val distance: Distance?,
    @SerializedName("duration") val duration: Duration?
)

data class Distance(
    @SerializedName("text") val text: String,
    @SerializedName("value") val value: Int
)

data class Duration(
    @SerializedName("text") val text: String,
    @SerializedName("value") val value: Int
)

data class DistanceResult(
    val distanceMeters: Int,
    val durationSeconds: Int
)
