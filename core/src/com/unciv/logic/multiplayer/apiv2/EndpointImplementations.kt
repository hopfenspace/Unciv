/**
 * Collection of endpoint implementations
 *
 * Those classes are not meant to be used directly. Take a look at the Api class for common usage.
 */

package com.unciv.logic.multiplayer.apiv2

import java.util.logging.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.util.*

/**
 * API wrapper for account handling (do not use directly; use the Api class instead)
 */
class AccountsApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

    /**
     * Retrieve information about the currently logged in user
     */
    suspend fun get(): AccountResponse {
        val response = client.get("/api/v2/accounts/me") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Retrieve details for an account by its UUID (always preferred to using usernames)
     */
    suspend fun lookup(uuid: UUID): AccountResponse {
        val response = client.get("/api/v2/accounts/$uuid") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Retrieve details for an account by its username
     *
     * Important note: Usernames can be changed, so don't assume they can be
     * cached to do lookups for their display names or UUIDs later. Always convert usernames
     * to UUIDs when handling any user interactions (e.g., inviting, sending messages, ...).
     */
    suspend fun lookup(username: String): AccountResponse {
        return lookup(LookupAccountUsernameRequest(username))
    }

    /**
     * Retrieve details for an account by its username
     *
     * Important note: Usernames can be changed, so don't assume they can be
     * cached to do lookups for their display names or UUIDs later. Always convert usernames
     * to UUIDs when handling any user interactions (e.g., inviting, sending messages, ...).
     */
    suspend fun lookup(r: LookupAccountUsernameRequest): AccountResponse {
        val response = client.post("/api/v2/accounts/lookup") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Update the currently logged in user information
     *
     * At least one value must be set to a non-null value.
     */
    suspend fun update(username: String?, displayName: String?): Boolean {
        return update(UpdateAccountRequest(username, displayName))
    }

    /**
     * Update the currently logged in user information
     *
     * At least one value must be set to a non-null value.
     */
    suspend fun update(r: UpdateAccountRequest): Boolean {
        val response = client.put("/api/v2/accounts/me") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Deletes the currently logged-in account
     */
    suspend fun delete(): Boolean {
        val response = client.delete("/api/v2/accounts/me") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            logger.info("The current user has been deleted")
            authCookieHelper.unset()
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Set a new password for the currently logged-in account, provided the old password was accepted as valid
     */
    suspend fun setPassword(oldPassword: String, newPassword: String): Boolean {
        return setPassword(SetPasswordRequest(oldPassword, newPassword))
    }

    /**
     * Set a new password for the currently logged-in account, provided the old password was accepted as valid
     */
    suspend fun setPassword(r: SetPasswordRequest): Boolean {
        val response = client.post("/api/v2/accounts/setPassword") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            logger.info("Password has been changed successfully")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Register a new user account
     */
    suspend fun register(username: String, displayName: String, password: String): Boolean {
        return register(AccountRegistrationRequest(username, displayName, password))
    }

    /**
     * Register a new user account
     */
    suspend fun register(r: AccountRegistrationRequest): Boolean {
        val response = client.post("/api/v2/accounts/register") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            logger.info("A new account for username ${r.username} has been created")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}

/**
 * API wrapper for authentication handling (do not use directly; use the Api class instead)
 */
class AuthApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

    /**
     * Try logging in with username and password for testing purposes, don't set the session cookie
     *
     * This method won't raise *any* exception, just return the boolean value if login worked.
     */
    suspend fun loginOnly(username: String, password: String): Boolean {
        val response = client.post("/api/v2/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        return response.status.isSuccess()
    }

    /**
     * Try logging in with username and password
     *
     * This method will also implicitly set a cookie in the in-memory cookie storage to authenticate further API calls
     */
    suspend fun login(username: String, password: String): Boolean {
        return login(LoginRequest(username, password))
    }

    /**
     * Try logging in with username and password
     *
     * This method will also implicitly set a cookie in the in-memory cookie storage to authenticate further API calls
     */
    suspend fun login(r: LoginRequest): Boolean {
        val response = client.post("/api/v2/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(r)
        }
        if (response.status.isSuccess()) {
            val authCookie = response.setCookie()["id"]
            logger.info("Received new session cookie: $authCookie")
            if (authCookie != null) {
                authCookieHelper.set(authCookie.value)
            }
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            if (err.statusCode == ApiStatusCode.LoginFailed) {
                return false
            }
            throw err
        }
    }

    /**
     * Logs out the currently logged in user
     *
     * This method will also clear the cookie on success only to avoid further authenticated API calls
     */
    suspend fun logout(): Boolean {
        val response = client.post("/api/v2/auth/logout")
        if (response.status.isSuccess()) {
            logger.info("Logged out successfully (dropping session cookie...)")
            authCookieHelper.unset()
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}

/**
 * API wrapper for chat room handling (do not use directly; use the Api class instead)
 */
class ChatApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

    /**
     * Retrieve all messages a user has access to
     *
     * In the response, you will find different categories, currently friend rooms and lobby rooms.
     */
    suspend fun list(): GetAllChatsResponse {
        val response = client.get("/api/v2/chats") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Retrieve the messages of a chatroom
     *
     * [GetChatResponse.members] holds information about all members that are currently in the chat room (including yourself)
     */
    suspend fun get(roomID: Long): GetChatResponse {
        val response = client.get("/api/v2/chats/$roomID") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}

/**
 * API wrapper for friend handling (do not use directly; use the Api class instead)
 */
class FriendApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

    /**
     * Retrieve a list of your established friendships
     */
    suspend fun list(): List<FriendResponse> {
        val response = client.get("/api/v2/friends") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GetFriendResponse = response.body()
            return responseBody.friends
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Retrieve a list of your open friendship requests (incoming and outgoing)
     *
     * If you have a request with ``from`` equal to your username, it means you
     * have requested a friendship, but the destination hasn't accepted yet.
     * In the other case, if your username is in ``to``, you have received a friend request.
     */
    suspend fun listRequests(): List<FriendRequestResponse> {
        val response = client.get("/api/v2/friends") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GetFriendResponse = response.body()
            return responseBody.friendRequests
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Retrieve a list of your open incoming friendship requests
     *
     * The argument [myUUID] should be filled with the username of the currently logged in user.
     */
    suspend fun listIncomingRequests(myUUID: UUID): List<FriendRequestResponse> {
        return listRequests().filter {
            it.to.uuid == myUUID
        }
    }

    /**
     * Retrieve a list of your open outgoing friendship requests
     *
     * The argument [myUUID] should be filled with the username of the currently logged in user.
     */
    suspend fun listOutgoingRequests(myUUID: UUID): List<FriendRequestResponse> {
        return listRequests().filter {
            it.from.uuid == myUUID
        }
    }

    /**
     * Request friendship with another user
     */
    suspend fun request(other: UUID): Boolean {
        return request(CreateFriendRequest(other))
    }

    /**
     * Request friendship with another user
     */
    suspend fun request(r: CreateFriendRequest): Boolean {
        val response = client.post("/api/v2/friends") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            logger.info("You have requested friendship with user ${r.uuid}")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Accept a friend request
     */
    suspend fun accept(friendRequestID: Long): Boolean {
        val response = client.delete("/api/v2/friends/$friendRequestID") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            logger.info("You have successfully accepted friendship request ID $friendRequestID")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Don't want your friends anymore? Just delete them!
     *
     * This function accepts both friend IDs and friendship request IDs,
     * since they are the same thing in the server's database anyways.
     */
    suspend fun delete(friendID: Long): Boolean {
        val response = client.delete("/api/v2/friends/$friendID") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            logger.info("You have successfully dropped friendship ID $friendID")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}

/**
 * API wrapper for game handling (do not use directly; use the Api class instead)
 */
class GameApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

    /**
     * Retrieves an overview of all open games of a player
     *
     * The response does not contain any full game state, but rather a
     * shortened game state identified by its ID and state identifier.
     * If the state ([GameOverviewResponse.gameDataID]) of a known game
     * differs from the last known identifier, the server has a newer
     * state of the game. The [GameOverviewResponse.lastActivity] field
     * is a convenience attribute and shouldn't be used for update checks.
     */
    suspend fun list(): List<GameOverviewResponse> {
        val response = client.get("/api/v2/games") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Retrieves a single game which is currently open (actively played)
     *
     * If the game has been completed or aborted, it will
     * respond with a GameNotFound in [ApiErrorResponse].
     */
    suspend fun get(gameUUID: UUID): GameStateResponse {
        val response = client.get("/api/v2/games/$gameUUID") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Upload a new game state for an existing game
     *
     * If the game can't be updated (maybe it has been already completed
     * or aborted), it will respond with a GameNotFound in [ApiErrorResponse].
     * Use the [gameUUID] retrieved from the server in a previous API call.
     *
     * On success, returns the new game data ID that can be used to verify
     * that the client and server use the same state (prevents re-querying).
     */
    suspend fun upload(gameUUID: UUID, gameData: String): Long {
        return upload(GameUploadRequest(gameData, gameUUID))
    }

    /**
     * Upload a new game state for an existing game
     *
     * If the game can't be updated (maybe it has been already completed
     * or aborted), it will respond with a GameNotFound in [ApiErrorResponse].
     *
     * On success, returns the new game data ID that can be used to verify
     * that the client and server use the same state (prevents re-querying).
     */
    suspend fun upload(r: GameUploadRequest): Long {
        val response = client.put("/api/v2/games") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GameUploadResponse = response.body()
            logger.info("The game with ID ${r.gameUUID} has been uploaded, the new data ID is ${responseBody.gameDataID}")
            return responseBody.gameDataID
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}

/**
 * API wrapper for invite handling (do not use directly; use the Api class instead)
 */
class InviteApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

    /**
     * Retrieve all invites for the executing user
     */
    suspend fun list(): List<GetInvite> {
        val response = client.get("/api/v2/invites") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GetInvitesResponse = response.body()
            return responseBody.invites
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Invite a friend to a lobby
     *
     * The executing user must be in the specified open lobby.
     * The invited friend must not be in a friend request state.
     */
    suspend fun new(friend: UUID, lobbyID: Long): Boolean {
        return new(CreateInviteRequest(friend, lobbyID))
    }

    /**
     * Invite a friend to a lobby
     *
     * The executing user must be in the specified open lobby.
     * The invited friend must not be in a friend request state.
     */
    suspend fun new(r: CreateInviteRequest): Boolean {
        val response = client.post("/api/v2/invites") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            logger.info("The friend ${r.friend} has been invited to lobby ${r.lobbyID}")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}

/**
 * API wrapper for lobby handling (do not use directly; use the Api class instead)
 */
class LobbyApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

    /**
     * Retrieves all open lobbies
     *
     * If hasPassword is true, the lobby is secured by a user-set password
     */
    suspend fun list(): List<LobbyResponse> {
        val response = client.get("/api/v2/lobbies") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GetLobbiesResponse = response.body()
            return responseBody.lobbies
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     */
    suspend fun open(name: String): Long {
        return open(CreateLobbyRequest(name, null, LOBBY_MAX_PLAYERS))
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     * ``max_players`` must be between 2 and 34 (inclusive).
     */
    suspend fun open(name: String, maxPlayers: Int): Long {
        return open(CreateLobbyRequest(name, null, maxPlayers))
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     * ``max_players`` must be between 2 and 34 (inclusive).
     * If password is an empty string, an error is returned.
     */
    suspend fun open(name: String, password: String?, maxPlayers: Int): Long {
        return open(CreateLobbyRequest(name, password, maxPlayers))
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     * ``max_players`` must be between 2 and 34 (inclusive).
     * If password is an empty string, an error is returned.
     */
    suspend fun open(r: CreateLobbyRequest): Long {
        val response = client.post("/api/v2/lobbies") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: CreateLobbyResponse = response.body()
            logger.info("A new lobby with ID ${responseBody.lobbyID} has been created")
            return responseBody.lobbyID
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}
