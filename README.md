## Construindo um aplicativo WebAuthn com Spring

Adicione a dependência do servidor Yubico WebAuthn

```xml
<dependency>
    <groupId>com.yubico</groupId>
    <artifactId>webauthn-server-core</artifactId>
    <version>1.12.1</version>
</dependency>
```

## Visão geral da camada de dados Spring JPA

O aplicativo armazena dois objetos de dados: usuários e credenciais.</br>
Os usuários são pessoas que usam o sistema e podem ter várias credenciais.</br> 
As credenciais contêm as informações necessárias para identificar e verificar um dispositivo implementando o protocolo Client to Authenticator (CTAP2).</br>

 CTAP2 é uma especificação que descreve a comunicação entre um autenticador de roaming e outro cliente/plataforma na camada de aplicativo, bem como ligações a uma variedade de protocolos de transporte que usam diferentes mídias físicas.

### Dados do usuário

Existem dois objetivos para os dados do usuário: rastrear a existência e exclusividade do usuário e suas credenciais e permitir a criação de dois objetos JavaScript para o navegador fazer solicitações da API WebAuthn: 

Para registro: `PublicKeyCredentialCreationOptions`</br>
para autenticação: `PublicKeyCredentialRequestOptions`</br> 

Pesquisar:</br>
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

O campo `username` é definido pelo usuário. Destina-se a ser exibido por elementos de interface do usuário no cliente como parte do processo de registro e autenticação, mas também é totalmente opcional no processo WebAuthn. </br>
</br>
As solicitações de identidade WebAuthn são feitas usando um identificador de usuário, uma sequência de bytes com comprimento máximo de 64.

Os dados de byte (como o identificador do usuário) são armazenados no banco de dados como um objeto binário grande (BLOB).</br>

Para converter esses campos de dados no ByteArrayobjeto que o aplicativo usa, a classe `ByteArrayAttributeConverter` implementa a interface `AttributeConverter` com a anotação `Converter`.

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

Na classe `AppUser`

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

### Dados de credencial

O servidor também armazena informações sobre as credenciais do usuário. Os campos `id` e `name`são usados para identificar o autenticador no lado do servidor.

Para autenticar uma credencial, o servidor constrói um objeto `AssertionRequest` que contém as informações do lado do servidor sobre qualquer credencial. 

O AssertionRequest é convertido em JSON e enviado para o navegador, que se transforma `PublicKeyCredentialRequestOptions` durante a cerimônia de autenticação do WebAuthn. 

Os campos obrigatórios são um credentialId, uma matriz de bytes aleatórios criada pelo autenticador que identifica o escopo de uma credencial.

Também é necessário A publicKey, que é outra matriz de bytes. Isso é gerado pelo algoritmo criptográfico do autenticador e é usado para verificar a autenticidade de uma assinatura de credencial fornecida durante a autenticação.

```java
@Entity
@Getter
@NoArgsConstructor
public class Authenticator {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column
    private String name;

    @Lob
    @Column(nullable = false)
    private ByteArray credentialId;

    @Lob
    @Column(nullable = false)
    private ByteArray publicKey;

    @ManyToOne
    private AppUser user;

    /* The authenticator potentially provides a range of additional information. This 
     * application stores some of it to enable functionality that could be useful for
     * a production-quality web authentication project.
    */

    @Column(nullable = false)
    private Long count;

    @Lob
    @Column(nullable = true)
    private ByteArray aaguid;
}
```

A W3 encoraja fortemente os autenticadores a implementar um campo de contagem de assinaturas que aumenta cada vez que o autenticador é usado. 

Ao armazenar o inteiro signCount de 32 bits fornecido pelo autenticador, o servidor pode verificar quantas vezes o autenticador foi usado.
Contagens crescentes são esperadas; se o autenticador relatar uma contagem decrescente, ele deve levantar uma bandeira vermelha.

O campo `aaguid` é um identificador que deve ser fornecido pelos autenticadores (mas nem sempre é), que identifica o tipo de credencial utilizada.</br>
Isso pode ser usado para verificar a marca e o modelo do autenticador. Além disso, pode ser útil para negar o acesso de autenticadores desatualizados com vulnerabilidades de segurança conhecidas.

A classe de dados da credencial tem um construtor que usa o Yubico `RegistrationResulte` o objeto `AuthenticatorAttestationResponse` (convertido de JavaScript para Java), junto com um nome de usuário e um nome para a credencial.

```java
public Authenticator(RegistrationResult result, 
                     AuthenticatorAttestationResponse response, 
                     AppUser user, 
                     String name) {
    Optional<AttestedCredentialData> attestationData = response.getAttestation()
        .getAuthenticatorData()
        .getAttestedCredentialData();
    this.credentialId = result.getKeyId().getId();
    this.publicKey = result.getPublicKeyCose();
    this.aaguid = attestationData.get().getAaguid();
    this.count = result.getSignatureCount();
    this.name = name;
    this.user = user;
}
```

Crie outro `CrudRepository`para gerenciar os objetos do tipo Authenticator.


#### Propriedades do aplicativo

Crie um bean de configuração para armazenar as propriedades do aplicativo:

Adicione configuração para localhost no seu `src/main/resources/application.properties`:


### Implementando a biblioteca do servidor WebAuthn

Agora que as classes de dados existem, a próxima etapa é criar classes que recuperem e armazenem dados da API WebAuthn. O Yubico fornece a `CredentialRepositoryinterface` para lidar com o armazenamento e pesquisa de credenciais.</br>
A classe `RegistrationService` do aplicativo de demonstração substitui as cinco funções necessárias para o funcionamento da interface.

#### interface de dados

A função `getCredentialIdsForUsername()` é usada durante o registro da credencial. Qualquer ID de credencial associado armazenado no banco de dados é retornado como um conjunto de objetos do tipo `PublicKeyCredentialDescriptor`.</br>
Essa lista de IDs de credenciais existentes é passada para a API WebAuthn como uma lista de credenciais excluídas para evitar que a mesma credencial seja registrada duas vezes.

A função `getUserHandleForUsername()` é chamada durante a autenticação de credencial quando o usuário fornece um nome de usuário.</br>
O autenticador procura as credenciais geradas com a ajuda da matriz de bytes de manipulação do usuário, que o servidor precisa armazenar para essa finalidade.</br>
O aplicativo de demonstração usa essa função para procurar o identificador do usuário que é passado para a função no navegador `credential.get()`.

A função `getUsernameForUserHandle()` permite que os aplicativos manipulem logins sem nomes de usuários.</br>
O servidor organiza e reconhece os usuários por meio de nomes de usuário, mas apenas identificadores de usuário são necessários para o funcionamento da API WebAuthn.</br>
A função pode ser usado em fluxos de login que não exigem nomes de usuário antecipadamente. Hipoteticamente, um aplicativo poderia dispensar totalmente os nomes de usuário e identificar os usuários exclusivamente por identificadores de matriz de bytes exclusivos, embora este aplicativo não explore essa possibilidade.

A função `lookup()` está ativa durante a etapa final de verificação de credencial. Um autenticador registrado fornece uma assinatura de asserção e um ID de credencial para a API WebAuthn do navegador, que é enviada ao servidor.</br>
O servidor procura uma credencial armazenada usando o ID da credencial e o identificador do usuário fornecidos pelo autenticador.</br>
Nesse ponto, o servidor usará o objeto retornado por essa função de pesquisa para validar a assinatura de asserção (e, opcionalmente, a contagem de assinaturas) do autenticador.

De maneira semelhante, a função `lookupAll()` retorna um conjunto de objetos do tipo `RegisteredCredential`.</br>
Em vez de validar a assinatura do autenticador, esta função garante que não existam várias credenciais cadastradas com o mesmo ID de credencial.

Yubico fornece um objeto `RelyingParty` que é responsável por lidar com todas as solicitações de autenticação no servidor.</br>
O aplicativo constrói e fornece esse objeto a todos os controladores. Como as credenciais do WebAuthn têm como escopo apenas um site, o nome do host, a origem e um nome de exibição do site são fornecidos pela parte confiável.

Na classe AppApplication defina o bean RelyingParty;</br>

#### controladores de autenticação

O controlador da web para o aplicativo cria rotas e constrói os dados necessários para o cliente fazer solicitações de API WebAuthn :

- Construindo strings JSON usadas nas cerimônias de registro e autenticação.
- Cache de informações intermediárias necessárias para a execução segura de uma cerimônia (o desafio nonce).
- Passando informações da cerimônia de criação da credencial do navegador para as classes de armazenamento de dados.

Crie uma AuthControllerclasse:

