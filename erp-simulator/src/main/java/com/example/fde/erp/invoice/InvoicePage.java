package com.example.fde.erp.invoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoicePage(List<InvoiceView> items, String nextCursor) {}
