@echo off
set PATH=C:\Users\pedro\tools\maven\apache-maven-3.9.16\bin;%PATH%
cd /d C:\Users\pedro\OneDrive\Desktop\Stream-Chat
echo === Instalando contracts ===
mvn -f contracts/pom.xml install -DskipTests
echo.
echo === Compilando (sin tests) ===
mvn compile -DskipTests
echo.
echo === Corriendo todos los tests unitarios ===
mvn test "-Dtest=ChatGroupTest,ChatMessageTest,SendMessageUseCaseTest,AddMemberUseCaseTest,RemoveMemberUseCaseTest,CreateGroupUseCaseTest,RegisterUserUseCaseTest,GetMessageHistoryUseCaseTest,PublishActivityUseCaseTest,ProcessActivityUseCaseTest,ExpireTypingStatesUseCaseTest,TypingStateRegistryTest,TypingStatePatternTest,AuditBridgeConsumerTest,StatusUpdatesConsumerTest,MessageAuditConsumerTest,PublisherDecoratorsTest,ContractSerializationTest,ArchitectureTest,ChatWebSocketControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
pause
