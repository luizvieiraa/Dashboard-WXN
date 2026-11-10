package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.exception.ResourceNotFoundException;
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
     *
     * <p>O cliente fica bloqueado ate o fim da transacao de quem chamou. Assim,
     * duas mensagens do mesmo cliente nao sao processadas ao mesmo tempo e o bot
     * nunca responde enquanto um atendente assume ou encerra a conversa.</p>
     */
    @Transactional
    public Customer findOrCreateByPhone(String phoneNumber) {
        return customerRepository.findByPhoneNumberForUpdate(phoneNumber)
                .orElseGet(() -> customerRepository.save(
                        Customer.builder()
                                .phoneNumber(phoneNumber)
                                .build()));
    }

    /**
     * Bloqueia o cliente ate o fim da transacao corrente. Deve ser chamado
     * antes de carregar a conversa, para que ela seja lida ja com o estado
     * mais recente.
     */
    @Transactional
    public void lockById(Long customerId) {
        customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado: id=" + customerId));
    }
}
