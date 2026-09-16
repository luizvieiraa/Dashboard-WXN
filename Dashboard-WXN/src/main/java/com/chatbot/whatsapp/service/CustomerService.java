package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Busca o cliente pelo telefone; se ainda nao existir, cria um novo
     * registro. Toda mensagem recebida via webhook passa por aqui, pois o
     * telefone e o unico dado garantido no payload do WhatsApp.
     */
    @Transactional
    public Customer findOrCreateByPhone(String phoneNumber) {
        return customerRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> customerRepository.save(
                        Customer.builder()
                                .phoneNumber(phoneNumber)
                                .build()));
    }
}
