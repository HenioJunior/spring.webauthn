#### Construindo um aplicativo WebAuthn com Spring

Adicione a dependência do servidor Yubico WebAuthn

```xml
<dependency>
    <groupId>com.yubico</groupId>
    <artifactId>webauthn-server-core</artifactId>
    <version>1.12.1</version>
</dependency>
```

1. Visão geral da camada de dados Spring JPA

O aplicativo armazena duas classes de objetos de dados: usuários e credenciais.</br>
Os usuários são pessoas que usam o sistema e podem ter várias credenciais.</br> 
As credenciais contêm as informações necessárias para identificar e verificar um dispositivo implementando o protocolo Client to Authenticator (CTAP2).</br>

 CTAP2 é uma especificação que descreve a comunicação entre um autenticador de roaming e outro cliente/plataforma na camada de aplicativo, bem como ligações a uma variedade de protocolos de transporte que usam diferentes mídias físicas.

2.  Dados do usuário
Existem dois objetivos para os dados do usuário: rastrear a existência e exclusividade do usuário e suas credenciais e permitir a criação de dois objetos JavaScript para o navegador fazer solicitações da API WebAuthn: 

Para registro: `PublicKeyCredentialCreationOptions`</br>
para autenticação: `PublicKeyCredentialRequestOptions`</br> 

Pesquisar:
https://developer.mozilla.org/en-US/docs/Web/API/PublicKeyCredentialRequestOptions
https://developer.mozilla.org/en-US/docs/Web/API/CredentialsContainer/create


Usando o Spring JPA, a classe de dados `AppUser` começa com campos tradicionais para organizar usuários em um sistema, um id para consulta de banco de dados e um exclusivo username para permitir a autoidentificação do usuário.

Os campos `displayName`e `handle`são usados ​​pelo servidor para criar requisições WebAuthn:

```java
@Entity
@Getter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String displayName;

    @Lob
    @Column(nullable = false, length = 64)
    private ByteArray handle;
}
```

O campo de nome de usuário é definido pelo usuário. Destina-se a ser exibido por elementos de interface do usuário no cliente como parte do processo de registro e autenticação, mas também é totalmente opcional no processo WebAuthn. </br>
De acordo com a Especificação WebAuthn , uma sequência de apelidos como username não é adequada para identificação, portanto, as solicitações de identidade WebAuthn são feitas usando um identificador de usuário, uma sequência de bytes com comprimento máximo de 64.

Os dados de byte (como o identificador do usuário) são armazenados no banco de dados como um objeto binário grande (BLOB). Para converter esses campos de dados no ByteArrayobjeto que o aplicativo usa, o aplicativo implementa a classe `AttributeConverter` JPA com a anotação `Converter`.

```java
@Converter(autoApply = true)
public class ByteArrayAttributeConverter implements AttributeConverter<ByteArray, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(ByteArray attribute) {
        return attribute.getBytes();
    }

    @Override
    public ByteArray convertToEntityAttribute(byte[] dbData) {
        return new ByteArray(dbData);
    }
}
```

O aplicativo converte classes de dados em objetos Java fornecidos pela biblioteca `Yubico` que podem produzir strings formatadas em JSON que o cliente passará para a API WebAuthn.</br>
A função `toUserIdentity()` converte a classe de dados `AuthUser` em um objeto `UserIdentity` que pode acessar o nome de usuário, o nome da tela e o importantíssimo identificador de usuário.

```java
public AppUser(UserIdentity user) {
    this.handle = user.getId();
    this.username = user.getName();
    this.displayName = user.getDisplayName();
}

public UserIdentity toUserIdentity() {
    return UserIdentity.builder()
        .name(getUsername())
        .displayName(getDisplayName())
        .id(getHandle())
        .build();
}
```

Crie um Spring Data CrudRepository para gerenciar os objetos AppUser;


