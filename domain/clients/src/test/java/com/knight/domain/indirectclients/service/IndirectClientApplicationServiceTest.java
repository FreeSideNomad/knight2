package com.knight.domain.indirectclients.service;

import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.api.commands.IndirectClientCommands.*;
import com.knight.domain.indirectclients.api.events.IndirectClientOnboarded;
import com.knight.domain.indirectclients.api.queries.IndirectClientQueries.IndirectClientSummary;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IndirectClientApplicationService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IndirectClientApplicationService Tests")
class IndirectClientApplicationServiceTest {

    @Mock
    private IndirectClientRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private IndirectClientApplicationService service;

    private final SrfClientId parentClientId = new SrfClientId("123456789");
    private final ProfileId profileId = ProfileId.of("online", parentClientId);

    @Nested
    @DisplayName("createIndirectClient()")
    class CreateIndirectClient {

        @Test
        @DisplayName("should create indirect client successfully")
        void shouldCreateIndirectClientSuccessfully() {
            // Given
            when(repository.getNextSequenceForClient(parentClientId)).thenReturn(1);

            CreateIndirectClientCmd cmd = new CreateIndirectClientCmd(
                parentClientId,
                profileId,
                "Test Business",
                null
            );

            // When
            IndirectClientId result = service.createIndirectClient(cmd);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.urn()).isEqualTo("indirect:srf:123456789:1");

            // Verify save was called
            ArgumentCaptor<IndirectClient> captor = ArgumentCaptor.forClass(IndirectClient.class);
            verify(repository).save(captor.capture());

            IndirectClient savedClient = captor.getValue();
            assertThat(savedClient.businessName()).isEqualTo("Test Business");
            assertThat(savedClient.parentClientId()).isEqualTo(parentClientId);
        }

        @Test
        @DisplayName("should create indirect client with related persons")
        void shouldCreateIndirectClientWithRelatedPersons() {
            // Given
            when(repository.getNextSequenceForClient(parentClientId)).thenReturn(2);

            RelatedPersonData person = new RelatedPersonData(
                "John Doe",
                PersonRole.ADMIN,
                Email.of("john@example.com"),
                Phone.of("1-555-1234")
            );

            CreateIndirectClientCmd cmd = new CreateIndirectClientCmd(
                parentClientId,
                profileId,
                "Test Business",
                List.of(person)
            );

            // When
            IndirectClientId result = service.createIndirectClient(cmd);

            // Then
            assertThat(result).isNotNull();

            ArgumentCaptor<IndirectClient> captor = ArgumentCaptor.forClass(IndirectClient.class);
            verify(repository).save(captor.capture());

            IndirectClient savedClient = captor.getValue();
            assertThat(savedClient.relatedPersons()).hasSize(1);
            assertThat(savedClient.relatedPersons().get(0).name()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("should publish IndirectClientOnboarded event")
        void shouldPublishIndirectClientOnboardedEvent() {
            // Given
            when(repository.getNextSequenceForClient(parentClientId)).thenReturn(1);

            CreateIndirectClientCmd cmd = new CreateIndirectClientCmd(
                parentClientId,
                profileId,
                "Test Business",
                null
            );

            // When
            service.createIndirectClient(cmd);

            // Then
            ArgumentCaptor<IndirectClientOnboarded> eventCaptor =
                ArgumentCaptor.forClass(IndirectClientOnboarded.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            IndirectClientOnboarded event = eventCaptor.getValue();
            assertThat(event.indirectClientId()).isEqualTo("indirect:srf:123456789:1");
            assertThat(event.parentClientId()).isEqualTo("srf:123456789");
            assertThat(event.businessName()).isEqualTo("Test Business");
            assertThat(event.onboardedAt()).isNotNull();
        }

        @Test
        @DisplayName("should generate sequential IDs")
        void shouldGenerateSequentialIds() {
            // Given
            when(repository.getNextSequenceForClient(parentClientId)).thenReturn(5);

            CreateIndirectClientCmd cmd = new CreateIndirectClientCmd(
                parentClientId,
                profileId,
                "Test Business",
                null
            );

            // When
            IndirectClientId result = service.createIndirectClient(cmd);

            // Then
            assertThat(result.urn()).isEqualTo("indirect:srf:123456789:5");
        }
    }

    @Nested
    @DisplayName("addRelatedPerson()")
    class AddRelatedPerson {

        @Test
        @DisplayName("should add related person successfully")
        void shouldAddRelatedPersonSuccessfully() {
            // Given
            IndirectClientId clientId = IndirectClientId.of(parentClientId, 1);
            IndirectClient client = IndirectClient.create(
                clientId,
                parentClientId,
                profileId,
                "Test Business",
                "system"
            );
            when(repository.findById(clientId)).thenReturn(Optional.of(client));

            AddRelatedPersonCmd cmd = new AddRelatedPersonCmd(
                clientId,
                "Jane Smith",
                PersonRole.CONTACT,
                Email.of("jane@example.com"),
                Phone.of("1-555-5678")
            );

            // When
            service.addRelatedPerson(cmd);

            // Then
            verify(repository).save(any(IndirectClient.class));
            assertThat(client.relatedPersons()).hasSize(1);
            assertThat(client.relatedPersons().get(0).name()).isEqualTo("Jane Smith");
        }

        @Test
        @DisplayName("should throw when client not found")
        void shouldThrowWhenClientNotFound() {
            // Given
            IndirectClientId clientId = IndirectClientId.of(parentClientId, 999);
            when(repository.findById(clientId)).thenReturn(Optional.empty());

            AddRelatedPersonCmd cmd = new AddRelatedPersonCmd(
                clientId,
                "Jane Smith",
                PersonRole.CONTACT,
                null,
                null
            );

            // When/Then
            assertThatThrownBy(() -> service.addRelatedPerson(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Indirect client not found");
        }
    }

    @Nested
    @DisplayName("getIndirectClientSummary()")
    class GetIndirectClientSummary {

        @Test
        @DisplayName("should return client summary")
        void shouldReturnClientSummary() {
            // Given
            IndirectClientId clientId = IndirectClientId.of(parentClientId, 1);
            IndirectClient client = IndirectClient.create(
                clientId,
                parentClientId,
                profileId,
                "Test Business",
                "system"
            );
            // Add a person to make it ACTIVE
            client.addRelatedPerson("Admin", PersonRole.ADMIN, null, null);
            when(repository.findById(clientId)).thenReturn(Optional.of(client));

            // When
            IndirectClientSummary summary = service.getIndirectClientSummary(clientId);

            // Then
            assertThat(summary.indirectClientUrn()).isEqualTo(clientId.urn());
            assertThat(summary.businessName()).isEqualTo("Test Business");
            assertThat(summary.status()).isEqualTo("ACTIVE");
            assertThat(summary.relatedPersonsCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw when client not found")
        void shouldThrowWhenClientNotFound() {
            // Given
            IndirectClientId clientId = IndirectClientId.of(parentClientId, 999);
            when(repository.findById(clientId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getIndirectClientSummary(clientId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Indirect client not found");
        }
    }
}
