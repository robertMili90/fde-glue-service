package com.example.fde.glue.erp;

import java.util.List;

public record InvoicePage(List<InvoiceSnapshot> items, String nextCursor) {}
