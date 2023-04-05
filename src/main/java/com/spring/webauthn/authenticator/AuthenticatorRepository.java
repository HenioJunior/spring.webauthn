package com.spring.webauthn.authenticator;

import com.spring.webauthn.user.AppUser;
import com.yubico.webauthn.data.ByteArray;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface AuthenticatorRepository extends CrudRepository<Authenticator, Long> {

    Optional<Authenticator> findByCredentialId(ByteArray credentialId);
    List<Authenticator> findAllByUser (AppUser user);
    List<Authenticator> findAllByCredentialId(ByteArray credentialId);

}
