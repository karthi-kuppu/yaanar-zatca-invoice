package com.yaanar.zatca.invoice.service;

import com.gazt.einvoicing.signing.service.SigningService;
import com.gazt.einvoicing.signing.service.impl.SigningServiceImpl;
import com.yaanar.zatca.invoice.model.*;
import com.zatca.config.ResourcesPaths;
import com.zatca.sdk.dto.ApplicationPropertyDto;
import com.zatca.sdk.service.CsrGenerationService;
import com.zatca.sdk.service.GeneratorTemplate;
import com.zatca.sdk.service.InvoiceRequestGenerationService;
import com.zatca.sdk.service.InvoiceSigningService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class ComplianceInvoiceService {

    public InvoiceResponse process(String otp, String encodedInvoice) throws Exception {
        InvoiceResponse invoiceResponse = new InvoiceResponse();

        ConcurrentMap<String, String> statusMap = initializeStatusMap();
        ResourcesPaths paths = ResourcesPaths.getInstance();
        var privateKey = paths.getPrivateKeyPath();
        log.info("Generate CSR Request..");
        generateCSR(privateKey);
        statusMap.put("Generate CSR", "PASSED");
        log.info("CSR Request Generated..");
        log.info("Retrieving Compliance CSID..");
        CSRResponse csrResponse = retrieveComplianceCSID(otp);
        statusMap.put("Retrieving Compliance CSID", "PASSED");
        log.info("Compliance CSID Retrieved Successfully..");
        var securityToken = csrResponse.getBinarySecurityToken();
        var securityPassword = csrResponse.getSecret();
        var csrRequestId = csrResponse.getRequestID();
        byte[] securityTokenBytes = securityToken.getBytes("UTF-8");
        var decodedCertificate = Base64.getDecoder().decode(securityTokenBytes);
        FileUtils.copyInputStreamToFile(new ByteArrayInputStream(decodedCertificate),
                new File(paths.getCertificatePath()));
        var invoicePath = "C:\\development\\zatca\\zatca-einvoicing-sdk-231-R3.1.7\\Apps";
        //var invoiceName = "invoice-standard-0";
        // var invoiceName = "xmlsample5";
        var invoiceName = "zatca-invoice";
        var timestamp = new SimpleDateFormat("yyyyMMddhhmmss").format(new Date());
        var invoiceXmlName = String.format("%s-%s.%s", invoiceName, timestamp, "xml");
        var signedInvoiceName = String.format("%s-%s-%s.%s", invoiceName, "signed",
                timestamp, "xml");
        var invoiceFilePath = invoicePath + "\\" + invoiceXmlName;
        byte[] invoiceBytes = encodedInvoice.getBytes("UTF-8");
        var decodedInvoice = Base64.getDecoder().decode(invoiceBytes);
        FileUtils.copyInputStreamToFile(new ByteArrayInputStream(decodedInvoice),
                new File(invoiceFilePath));
        log.info("Signing Invoice..");
        String invoiceFile = Files.readString(Path.of(invoiceFilePath));
        var invoiceHash = getInvoiceHash(invoiceFile);
        generateSignedInvoice(invoicePath, invoiceXmlName, signedInvoiceName);
        statusMap.put("Sign Invoice", "PASSED");
        log.info("Invoice Signed Successfully..");
        Path signedInvoicePath = Path.of(invoicePath + "\\" + invoiceXmlName);
        String signedInvoice = Files.readString(signedInvoicePath);
        byte[] signedInvoiceBytes = signedInvoice.getBytes("UTF-8");
        var encodedSignedInvoice = Base64.getEncoder().encode(signedInvoiceBytes);
        var strEncodedSignedInvoice =  new String(encodedSignedInvoice, "UTF-8");
        var invoiceRequest = generateInvoiceRequest(invoicePath, signedInvoiceName);
        log.info("Invoice Request:{}", invoiceRequest);
        log.info("Requesting Compliance Report for Invoice... ");
        ComplianceResponse complianceResponse = reportComplianceInvoice(invoiceRequest, securityToken, securityPassword);
        log.info("Compliance Report Clearance Status: {}", complianceResponse.getClearanceStatus());
        statusMap.put("Sign Invoice", complianceResponse.getClearanceStatus());
        log.info("Retrieving Production CSID..");
        CSRResponse prodCSRResponse = retrieveProductionCSID(otp, csrRequestId, securityToken, securityPassword);
        log.info("Production CSID Retrieved..");
        statusMap.put("Retrieving Production CSID", "PASSED");
        log.info("Requesting Invoice Report... ");
        ProductionReportResponse reportResponse = reportProductionInvoice(invoiceRequest, prodCSRResponse.getBinarySecurityToken(), prodCSRResponse.getSecret());
        log.info("Invoice Reporting Status:{}", reportResponse.getReportingStatus());
        statusMap.put("Invoice Reporting Status", reportResponse.getReportingStatus());
        invoiceResponse.setSignedInvoice(strEncodedSignedInvoice);
        invoiceResponse.setInvoiceHash(invoiceHash);
        invoiceResponse.setComplianceResponse(complianceResponse);
        return invoiceResponse;
    }

    private CSRResponse retrieveComplianceCSID(String otp) throws Exception {
        var complianceURL = "https://gw-apic-gov.gazt.gov.sa/e-invoicing/developer-portal/compliance";
        Path filePath = Path.of("C:\\development\\zatca\\certs\\taxpayer0.csr");
        String content = Files.readString(filePath);
        CSRRequest csrRequest = new CSRRequest();
        csrRequest.setCsr(content);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("OTP", otp);
        httpHeaders.add("Accept-Version", "V2");
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CSRRequest> request = new HttpEntity<>(csrRequest, httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<CSRResponse> response = restTemplate
                .exchange(complianceURL, HttpMethod.POST, request, CSRResponse.class);
        return response.getBody();
    }
    private CSRResponse retrieveProductionCSID(String otp, String complianceRequestId, String userName, String password) throws Exception {
        var prodCSIDURL = "https://gw-apic-gov.gazt.gov.sa/e-invoicing/developer-portal/production/csids";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("OTP", otp);
        httpHeaders.add("Accept-Version", "V2");
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setBasicAuth(userName, password);
        ProductionCSIDRequest productionCSIDRequest = new ProductionCSIDRequest();
        productionCSIDRequest.setComplianceRequestId(complianceRequestId);
        HttpEntity<ProductionCSIDRequest> request = new HttpEntity<>(productionCSIDRequest, httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<CSRResponse> response = restTemplate
                .exchange(prodCSIDURL, HttpMethod.POST, request, CSRResponse.class);
        return response.getBody();
    }
    private ComplianceResponse reportComplianceInvoice(String invoiceRequest, String userName, String password) throws Exception {
        var complianceURL = "https://gw-apic-gov.gazt.gov.sa/e-invoicing/developer-portal/compliance/invoices";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Accept-Language", "en");
        httpHeaders.add("Accept-Version", "V2");
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setBasicAuth(userName, password);
        HttpEntity<String> request = new HttpEntity<>(invoiceRequest, httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<ComplianceResponse> response = restTemplate
                .exchange(complianceURL, HttpMethod.POST, request, ComplianceResponse.class);
        return response.getBody();
    }
    private ProductionReportResponse reportProductionInvoice(String invoiceRequest, String userName, String password) throws Exception {
        var prodReportURL = "https://gw-apic-gov.gazt.gov.sa/e-invoicing/developer-portal/invoices/reporting/single";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Accept-Language", "en");
        httpHeaders.add("Accept-Version", "V2");
        httpHeaders.add("Clearance-Status", "0");
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setBasicAuth(userName, password);
        HttpEntity<String> request = new HttpEntity<>(invoiceRequest, httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<ProductionReportResponse> response = restTemplate
                .exchange(prodReportURL, HttpMethod.POST, request, ProductionReportResponse.class);
        return response.getBody();
    }
    private String generateInvoiceRequest(String invoicePath, String invoiceName) {
        var invoiceRequest = "";
        var invoice = invoicePath.concat(String.valueOf(File.separatorChar)).concat(invoiceName);
        try {
            var xmlFile = FileUtils.readFileToString(new File(invoice), StandardCharsets.UTF_8);
            InvoiceRequestGenerationService invoiceRequestGenerationService = new InvoiceRequestGenerationService();
            Properties invoiceProps = invoiceRequestGenerationService.getInvoiceData(xmlFile);
            invoiceRequest = invoiceRequestGenerationService.prepareRequestBodyString(invoiceProps);
        } catch (Exception exception) {
            log.error("unable to generate invoice hash" + exception);
        }
        return invoiceRequest;
    }
    private void generateCSR(String privateKeyFile) {
        GeneratorTemplate generateService = new CsrGenerationService();
        ApplicationPropertyDto configuration = new ApplicationPropertyDto();
        configuration.setGenerateCsr(true);
        configuration.setCsrConfigFileName("C:\\development\\zatca\\certs\\csr-config-taxpayer.properties");
        configuration.setCsrFileName("C:\\development\\zatca\\certs\\taxpayer0.csr");
        configuration.setPrivateKeyFileName(privateKeyFile);
        generateService.generate(configuration);
    }
    private void generateSignedInvoice(String invoicePath, String invoiceName, String signedInvoiceName) {
        GeneratorTemplate generateService = new InvoiceSigningService();
        ApplicationPropertyDto configuration = new ApplicationPropertyDto();
        configuration.setGenerateSignature(true);
        configuration.setInvoiceFileName(invoicePath.concat(String.valueOf(File.separatorChar)).concat(invoiceName));
        configuration.setOutputInvoiceFileName(invoicePath.concat(String.valueOf(File.separatorChar)).concat(signedInvoiceName));
        generateService.generate(configuration);
    }

    private ConcurrentMap<String, String>  initializeStatusMap() {
        ConcurrentMap<String, String> statusMap = new ConcurrentHashMap<>();
        statusMap.put("Generate CSR", "PENDING");
        statusMap.put("Retrieving Compliance CSID", "PENDING");
        statusMap.put("Sign Invoice", "PENDING");
        statusMap.put("Retrieving Production CSID", "PENDING");
        statusMap.put("Invoice Reporting Status", "PENDING");
        return statusMap;
    }

    private String getInvoiceHash(String invoice) throws Exception {
        SigningService signingService = new SigningServiceImpl();
        return signingService.generateInvoiceHash(invoice);
    }
}
