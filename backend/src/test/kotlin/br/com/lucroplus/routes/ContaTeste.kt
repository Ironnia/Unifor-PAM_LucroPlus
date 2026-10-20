package br.com.lucroplus.routes

import br.com.lucroplus.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun prepararContaTeste(admin: Boolean = false) {
    if (RestaurantesTable.selectAll().where { RestaurantesTable.id eq 1L }.empty()) RestaurantesTable.insert { it[id] = 1; it[nome] = "Loja teste" }
    if (UsuariosTable.selectAll().where { UsuariosTable.id eq 1L }.empty()) UsuariosTable.insert {
        it[id] = 1; it[nome] = "Conta teste"; it[email] = "teste@example.invalid"
        it[senhaHash] = "hash-ficticio"; it[tipo] = if (admin) "ADMIN" else "GERENTE"
        it[restauranteId] = 1; it[ativo] = true
    }
}

fun bancoContaTeste(admin: Boolean = false): Database {
    val banco = Database.connect("jdbc:h2:mem:conta_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")
    TransactionManager.defaultDatabase = banco
    transaction(banco) {
        SchemaUtils.create(RestaurantesTable, UsuariosTable, ConfiguracoesRestauranteTable)
        prepararContaTeste(admin)
    }
    return banco
}
