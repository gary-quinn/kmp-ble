package com.atruedev.kmpble.sample.detail.bonding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atruedev.kmpble.bonding.BondState

@Composable
internal fun BondingSection(
    bondState: BondState?,
    selectedRecipe: ConnectionRecipeType?,
    onPair: () -> Unit,
    onRemoveBond: () -> Unit,
    onSelectRecipe: (ConnectionRecipeType) -> Unit,
    onConnectWithRecipe: (ConnectionRecipeType) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Bonding", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            BondStateRow(bondState = bondState, onPair = onPair, onRemoveBond = onRemoveBond)
            Spacer(modifier = Modifier.height(8.dp))

            ConnectionRecipeRow(
                selectedRecipe = selectedRecipe,
                onSelectRecipe = onSelectRecipe,
                onConnect = onConnectWithRecipe,
            )
        }
    }
}

@Composable
private fun BondStateRow(
    bondState: BondState?,
    onPair: () -> Unit,
    onRemoveBond: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Bond: ${bondStateLabel(bondState)}",
            style = MaterialTheme.typography.bodySmall,
        )
        when (bondState) {
            BondState.Bonded ->
                OutlinedButton(onClick = onRemoveBond) {
                    Text("Remove Bond", style = MaterialTheme.typography.labelSmall)
                }
            else ->
                OutlinedButton(onClick = onPair) {
                    Text("Pair", style = MaterialTheme.typography.labelSmall)
                }
        }
    }
}

@Composable
private fun ConnectionRecipeRow(
    selectedRecipe: ConnectionRecipeType?,
    onSelectRecipe: (ConnectionRecipeType) -> Unit,
    onConnect: (ConnectionRecipeType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeDropdown(
            selectedRecipe = selectedRecipe,
            onSelectRecipe = onSelectRecipe,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = { selectedRecipe?.let(onConnect) },
            enabled = selectedRecipe != null,
        ) {
            Text("Connect", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecipeDropdown(
    selectedRecipe: ConnectionRecipeType?,
    onSelectRecipe: (ConnectionRecipeType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                selectedRecipe?.let { recipeLabel(it) } ?: "Select recipe...",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ConnectionRecipeType.entries.forEach { recipe ->
                DropdownMenuItem(
                    text = { Text(recipeLabel(recipe)) },
                    onClick = {
                        onSelectRecipe(recipe)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun bondStateLabel(bondState: BondState?): String =
    when (bondState) {
        BondState.Bonded -> "Bonded"
        BondState.Bonding -> "Bonding..."
        BondState.NotBonded -> "Not Bonded"
        BondState.Unknown -> "Unknown"
        null -> "Unknown"
    }

private fun recipeLabel(recipe: ConnectionRecipeType): String =
    when (recipe) {
        ConnectionRecipeType.MEDICAL -> "Medical"
        ConnectionRecipeType.FITNESS -> "Fitness"
        ConnectionRecipeType.IOT -> "IoT"
        ConnectionRecipeType.CONSUMER -> "Consumer"
    }
