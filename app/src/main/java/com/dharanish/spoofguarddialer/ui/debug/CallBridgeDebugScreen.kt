package com.dharanish.spoofguarddialer.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CallBridgeDebugScreen(
    modifier: Modifier = Modifier,
    viewModel: CallBridgeDebugViewModel = viewModel()
) {
    var testNumber by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Programmatic Bridge Test",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Active calls tracked: ${viewModel.activeCallCount}",
            modifier = Modifier.padding(top = 16.dp)
        )

        OutlinedTextField(
            value = testNumber,
            onValueChange = { testNumber = it },
            label = { Text("Test number (a second real phone)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Button(
            onClick = { viewModel.startBridge(testNumber) },
            enabled = testNumber.isNotBlank() && viewModel.activeCallCount > 0,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Start Bridge (place + merge)")
        }

        Text(text = viewModel.statusMessage, modifier = Modifier.padding(top = 16.dp))
    }
}