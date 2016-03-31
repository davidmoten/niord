/**
 * Services that handles authentication and authorization via the backend
 */
angular.module('niord.auth')

    /**
     * Interceptor that adds a Keycloak access token to the requests as an authorization header.
     */
    .factory('authHttpInterceptor', ['$q', '$window', 'AuthService',
        function($q, $window, AuthService) {
            return {

                'request': function(config) {
                    var deferred = $q.defer();
                    config.headers = config.headers || {};

                    if ($window.localStorage.domain) {
                        config.headers['NiordDomain'] = $window.localStorage.domain;
                    }

                    if (AuthService.keycloak.token) {
                        AuthService.keycloak.updateToken(60).success(function() {
                            config.headers.Authorization = 'Bearer ' + AuthService.keycloak.token;
                            deferred.resolve(config);
                        }).error(function() {
                            deferred.reject('Failed to refresh token');
                        });
                    } else {
                        // Not authenticated - leave it to the server to fail
                        deferred.resolve(config);
                    }
                    return deferred.promise;
                },

                'responseError': function(response) {
                    if (response.status == 401) {
                        console.error('session timeout?');
                        AuthService.logout();
                    } else if (response.status == 403) {
                        console.error('Forbidden');
                    } else if (response.status == 404) {
                        console.error('Not found');
                    } else if (response.status) {
                        if (response.data && response.data.errorMessage) {
                            console.error(response.data.errorMessage);
                        } else {
                            console.error("An unexpected server error has occurred " + response.status);
                        }
                    } else if (response == "Failed to refresh token") {
                        AuthService.logout();
                    }
                    return $q.reject(response);
                }
            };
        }])


    /**
     * Register global functions available on root scope
     */
    .run(['$rootScope', '$location',
        function ($rootScope, $location) {

            // Navigate to the given path
            $rootScope.go = function (path) {
                $location.path(path);
            };
            
        }]);


var auth = {};

/**
 * Will bootstrap Keycloak and register the "Auth" service
 * @param angularAppName the angular modules
 */
function bootstrapKeycloak(angularAppName, onLoad) {
    var keycloak = new Keycloak('keycloak.json');
    auth.loggedIn = false;

    var initProps = {};
    if (onLoad) {
        initProps.onLoad = onLoad;
    }

    keycloak.init(
        initProps
    ).success(function (authenticated) {

        auth.loggedIn = authenticated;
        auth.keycloak = keycloak;

        // Returns whether the current user has any roles defined for the given client
        auth.hasRolesFor = function (clientId) {
            if (!keycloak.resourceAccess) {
                return false;
            }
            var access = keycloak.resourceAccess[clientId];
            return !!access && access.roles.length > 0;
        };
        
        
        // Performs a log-in and sets the return url to the given value
        auth.login = function (returnUrl) {
            var loginOpts = {};
            if (returnUrl) {
                loginOpts.redirectUri = returnUrl;
            }
            keycloak.login(loginOpts);
        };


        // Logs out Keycloak
        auth.logout = function () {
            keycloak.logout();
            auth.loggedIn = false;
        };

        // Register the Auth factory
        app.factory('AuthService', function() {
            return auth;
        });

        angular.bootstrap(document, [ angularAppName ]);

    }).error(function () {
        window.location.reload();
    });

}
