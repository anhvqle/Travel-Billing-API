package com.travelbilling.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.travelbilling.invoice.InvoiceService;
import com.travelbilling.invoice.model.Invoice;
import com.travelbilling.invoice.model.InvoiceSummary;
import com.travelbilling.invoice.request.CreateInvoiceRequest;
import com.travelbilling.shared.Page;

/** HTTP endpoints for invoices. Only translates HTTP to service calls; all rules live in InvoiceService. */
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Invoice create(@RequestBody CreateInvoiceRequest request) {
        return invoiceService.create(request);
    }

    @GetMapping
    public Page<InvoiceSummary> list(@RequestParam(required = false) Integer limit,
                                     @RequestParam(required = false) String cursor) {
        return invoiceService.list(limit, cursor);
    }

    @GetMapping("/{id}")
    public Invoice get(@PathVariable String id) {
        return invoiceService.get(id);
    }

    @PatchMapping("/{id}")
    public Invoice update(@PathVariable String id, @RequestBody CreateInvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        invoiceService.delete(id);
    }
}
