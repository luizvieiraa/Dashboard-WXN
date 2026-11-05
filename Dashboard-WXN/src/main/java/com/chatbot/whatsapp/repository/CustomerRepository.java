package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Customer;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhoneNumber(String phoneNumber);

    /**
     * Busca o cliente com bloqueio de escrita ({@code SELECT ... FOR UPDATE}) ate
     * o fim da transacao. Serializa o processamento de mensagens do mesmo
     * cliente e as acoes do atendente sobre as conversas dele.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.phoneNumber = :phoneNumber")
    Optional<Customer> findByPhoneNumberForUpdate(@Param("phoneNumber") String phoneNumber);

    /** Mesmo bloqueio de {@link #findByPhoneNumberForUpdate(String)}, pelo id. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);
}
