                    )
                                        }
                                    }
                                }
                            }

                            // Section: BLE Devices
                            if (bleDevices.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "BLE SCAN · ELM327 ADAPTERS",
                                        color = SecondaryText(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                items(bleDevices) { device ->
                                    val devName = BluetoothDeviceInfo.displayName(context, device, "Unknown BLE Device")
                                    val deviceAddress = BluetoothDeviceInfo.address(context, device)
                                    val isObd = devName.lowercase().contains("obd") ||
                                            devName.lowercase().contains("elm") ||
                                            devName.lowercase().contains("vgate") ||
                                            devName.lowercase().contains("carista")

                                    Surface(
                                        color = DarkSurface(),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isObd) ElectricCyan().copy(alpha = 0.5f) else DarkSurfaceElevated()
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    color = DarkSurfaceElevated(),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Bluetooth,
                                                        contentDescription = null,
                                                        tint = if (isObd) ElectricCyan() else SecondaryText(),
                                                        modifier = Modifier
                                                            .padding(8.dp)
                                                            .size(20.dp)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = devName,
                                                        color = PrimaryText(),
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.SansSerif
                                                    )
                                                    if (isObd) {
                                                        Text(
                                                            text = "ELM327 MATCH",
                                                            color = ElectricCyan(),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.SansSerif,
                                                            modifier = Modifier
                                                                .background(ElectricCyan().copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = deviceAddress?.let { "MAC: $it" } ?: "MAC unavailable",
                                                        color = SecondaryText(),
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.SansSerif
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = { runCatching { connectSelected(device, true) } },
                                                enabled = canConnect && rowState.enabled && pendingPairDevice == null,
                                                colors = ButtonDefaults.buttonColors(containerColor = if (isObd) ElectricCyan() else DarkSurfaceElevated()),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Connect", color = OnAccent(), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: RAW TERMINAL
                if (selectedTab == 1) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Terminal input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = commandInput,
                                onValueChange = { commandInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("AT command (e.g. ATI, ATRV)", color = SecondaryText()) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan(),
                                    unfocusedBorderColor = DarkSurfaceElevated(),
                                    focusedTextColor = OnAccent(),
                                    unfocusedTextColor = SecondaryText(),
                                    cursorColor = ElectricCyan()
                                ),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (commandInput.isNotBlank()) {
                                        onSendCommand(commandInput)
                                        commandInput = ""
                                    }
                                },
                                enabled = commandInput.isNotBlank() && connectionState is ConnectionState.Connected,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan()),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send", color = OnAccent(), fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Terminal output
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(DarkSurface(), RoundedCornerShape(8.dp)),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(terminalLogs) { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = log.time,
                                        color = SecondaryText(),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(60.dp)
                                    )

                                    Surface(
                                        color = when (log.direction) {
                                            "TX" -> ElectricCyan().copy(alpha = 0.15f)
                                            "RX" -> TealAccent().copy(alpha = 0.15f)
                                            "INFO" -> DarkSurfaceElevated().copy(alpha = 0.15f)
                                            else -> CriticalRed().copy(alpha = 0.15f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = log.direction,
                                            color = when (log.direction) {
                                                "TX" -> ElectricCyan()
                                                "RX" -> TealAccent()
                                                "INFO" -> SecondaryText()
                                                else -> CriticalRed()
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = log.text,
                                        color = when (log.direction) {
                                            "TX" -> ElectricCyan()
                                            "RX" -> TealAccent()
                                            "INFO" -> SecondaryText()
                                            else -> CriticalRed()
                                        },
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 2: TROUBLESHOOTING
                if (selectedTab == 2) {
                    Column(modifier = Modifier.weight(1f)) {
                        Spacer(modifier = Modifier.height(8.dp))

                        listOf(
                            "1. OBD-II Port Location" to "On Kia Sportage 2.0 MPI, the 16-pin DLC port is located below the driver's dashboard near the hood release latch.",
                            "2. Ignition Required" to "Turn car ignition to 'ON' (or start engine). An unpowered ECU will not respond to PID queries.",
                            "3. Initial Pairing" to "In Android Settings > Bluetooth, pair with 'OBDII', 'V-LINK', or 'ELM327'. Typical PIN is 1234 or 0000.",
                            "4. Chinese Head Unit Pairing" to "On Android Head Units (TS10, UIS7862, MTK), ensure head unit Bluetooth PIN in factory settings matches the adapter (1234).",
                            "5. Supported Protocol" to "Kia Sportage MPI AWD communicates over ISO 15765-4 CAN (11-bit ID, 500 kbaud). The launcher automatically initializes this protocol."
                        ).forEach { (title, desc) ->
                            Text(title, color = PrimaryText(), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text(desc, color = SecondaryText(), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}
