package com.portal.serasa.application.service;

/** Formatação compartilhada de endereço para a praça de pagamento. */
final class AddressFormat {

    private AddressFormat() {
    }

    static String format(String street, String number, String complement,
                         String district, String city, String uf, String zip) {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) {
            sb.append(street);
            if (number != null && !number.isBlank()) {
                sb.append(", ").append(number);
            }
        }
        if (complement != null && !complement.isBlank()) {
            sb.append(sb.length() > 0 ? " - " : "").append(complement);
        }
        if (district != null && !district.isBlank()) {
            sb.append(sb.length() > 0 ? " - " : "").append(district);
        }
        if (city != null && !city.isBlank()) {
            sb.append(sb.length() > 0 ? ", " : "").append(city);
            if (uf != null && !uf.isBlank()) {
                sb.append("/").append(uf);
            }
        }
        if (zip != null && !zip.isBlank()) {
            sb.append(sb.length() > 0 ? " - CEP " : "CEP ").append(zip);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
