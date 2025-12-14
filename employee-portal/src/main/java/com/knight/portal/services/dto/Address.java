package com.knight.portal.services.dto;

import lombok.Data;

@Data
public class Address {
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince;
    private String zipPostalCode;
    private String countryCode;

    public String getFormattedAddress() {
        StringBuilder formatted = new StringBuilder();

        if (addressLine1 != null && !addressLine1.isEmpty()) {
            formatted.append(addressLine1);
        }

        if (addressLine2 != null && !addressLine2.isEmpty()) {
            formatted.append(", ").append(addressLine2);
        }

        if (city != null && !city.isEmpty()) {
            if (formatted.length() > 0) formatted.append(", ");
            formatted.append(city);
        }

        if (stateProvince != null && !stateProvince.isEmpty()) {
            if (formatted.length() > 0) formatted.append(", ");
            formatted.append(stateProvince);
        }

        if (zipPostalCode != null && !zipPostalCode.isEmpty()) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(zipPostalCode);
        }

        if (countryCode != null && !countryCode.isEmpty()) {
            if (formatted.length() > 0) formatted.append(", ");
            formatted.append(countryCode);
        }

        return formatted.toString();
    }

    /**
     * Returns a multi-line formatted address for display.
     */
    public String[] getAddressLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();

        if (addressLine1 != null && !addressLine1.isEmpty()) {
            lines.add(addressLine1);
        }

        if (addressLine2 != null && !addressLine2.isEmpty()) {
            lines.add(addressLine2);
        }

        StringBuilder cityLine = new StringBuilder();
        if (city != null && !city.isEmpty()) {
            cityLine.append(city);
        }
        if (stateProvince != null && !stateProvince.isEmpty()) {
            if (cityLine.length() > 0) cityLine.append(", ");
            cityLine.append(stateProvince);
        }
        if (zipPostalCode != null && !zipPostalCode.isEmpty()) {
            if (cityLine.length() > 0) cityLine.append(" ");
            cityLine.append(zipPostalCode);
        }
        if (cityLine.length() > 0) {
            lines.add(cityLine.toString());
        }

        if (countryCode != null && !countryCode.isEmpty()) {
            lines.add(countryCode);
        }

        return lines.toArray(new String[0]);
    }
}
