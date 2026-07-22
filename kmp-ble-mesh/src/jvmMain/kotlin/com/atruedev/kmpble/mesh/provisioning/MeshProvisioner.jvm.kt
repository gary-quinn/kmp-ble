package com.atruedev.kmpble.mesh.provisioning

import com.atruedev.kmpble.mesh.MeshNotSupported

public actual fun MeshProvisioner(): MeshProvisioner =
    throw MeshNotSupported()
