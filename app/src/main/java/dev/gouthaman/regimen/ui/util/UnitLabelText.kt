package dev.gouthaman.regimen.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.domain.util.UnitLabel

@Composable
fun UnitLabel.text(): String = when (this) {
    UnitLabel.KG -> stringResource(R.string.unit_label_kg)
    UnitLabel.LB -> stringResource(R.string.unit_label_lb)
    UnitLabel.KM -> stringResource(R.string.unit_label_km)
    UnitLabel.MI -> stringResource(R.string.unit_label_mi)
}
