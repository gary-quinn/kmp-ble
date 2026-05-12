package com.atruedev.kmpble.l2cap

import com.atruedev.kmpble.KmpBle

public actual fun L2capListener(): L2capListener = AndroidL2capListener(KmpBle.requireContext())
