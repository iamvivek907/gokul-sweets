package com.gokulsweets.restaurant.printing.enums;

/** Defines the supported printer protocol values. */
public enum PrinterProtocol {

    /** The esc pos tcp value. */
    ESC_POS_TCP,

    /** ESC/POS sent as RAW data to a Windows USB printer queue by the local agent. */
    ESC_POS_USB,

    /** ESC/POS sent to a paired Bluetooth Classic serial port by the local agent. */
    ESC_POS_BLUETOOTH
}
