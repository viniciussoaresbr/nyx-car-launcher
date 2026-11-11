# Regras do R8 específicas do projeto.
# Nenhuma regra extra é necessária por enquanto: o app não usa reflexão.

# Nomes de classes e linhas legíveis nos erros gravados no diário do app (CrashRecorder, 0.4.1):
# a central não tem ADB, então não há como desfazer a ofuscação depois. O R8 continua
# removendo código sem uso e otimizando.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
