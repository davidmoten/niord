
/**
 * Controllers for message list dialogs
 */
angular.module('niord.messages')

    /*******************************************************************
     * Controller that handles displaying message details in a dialog
     *******************************************************************/
    .controller('MessageDialogCtrl', ['$scope', '$window', 'growl', 'MessageService', 'messageId', 'messages', 'selection',
        function ($scope, $window, growl, MessageService, messageId, messages, selection) {
            'use strict';

            $scope.warning = undefined;
            $scope.messages = messages;
            $scope.pushedMessageIds = [];
            $scope.pushedMessageIds[0] = messageId;
            $scope.selection = selection;

            $scope.msg = undefined;
            $scope.index = $.inArray(messageId, messages);
            $scope.showNavigation = $scope.index >= 0;

            // Attempt to improve printing
            $("body").addClass("no-print");
            $scope.$on("$destroy", function() {
                $("body").removeClass("no-print");
            });

            // Navigate to the previous message in the message list
            $scope.selectPrev = function() {
                if ($scope.pushedMessageIds.length == 1 && $scope.index > 0) {
                    $scope.index--;
                    $scope.pushedMessageIds[0] = $scope.messages[$scope.index];
                    $scope.loadMessageDetails();
                }
            };

            // Navigate to the next message in the message list
            $scope.selectNext = function() {
                if ($scope.pushedMessageIds.length == 1 && $scope.index >= 0 && $scope.index < $scope.messages.length - 1) {
                    $scope.index++;
                    $scope.pushedMessageIds[0] = $scope.messages[$scope.index];
                    $scope.loadMessageDetails();
                }
            };

            // Navigate to a new nested message
            $scope.selectMessage = function (messageId) {
                $scope.pushedMessageIds.push(messageId);
                $scope.loadMessageDetails();
            };

            // Navigate back in the nested navigation
            $scope.back = function () {
                if ($scope.pushedMessageIds.length > 1) {
                    $scope.pushedMessageIds.pop();
                    $scope.loadMessageDetails();
                }
            };

            // Return the currently diisplayed message id
            $scope.currentMessageId = function() {
                return $scope.pushedMessageIds[$scope.pushedMessageIds.length - 1];
            };

            // Load the message details for the given message id
            $scope.loadMessageDetails = function() {

                MessageService.details($scope.currentMessageId())
                    .success(function (data) {
                        $scope.warning = (data) ? undefined : "Message " + $scope.currentMessageId() + " not found";
                        $scope.msg = data;
                    })
                    .error(function () {
                        $scope.msg = undefined;
                        growl.error('Message Lookup Failed', { ttl: 5000 });
                    });
            };

            $scope.loadMessageDetails();


            // Returns if the given message is selected or not
            $scope.isSelected = function () {
                return $scope.selection && $scope.currentMessageId() &&
                    $scope.selection.get($scope.currentMessageId()) !== undefined;
            };


            // Toggle the selection state of the message
            $scope.toggleSelectMessage = function () {
                if ($scope.isSelected()) {
                    $scope.selection.remove($scope.currentMessageId());
                } else if ($scope.msg && $scope.selection) {
                    $scope.selection.put($scope.currentMessageId(), angular.copy($scope.msg));
                }
            };


            // Returns the select button class
            $scope.selectBtnClass = function () {
                return $scope.isSelected() ? "btn-danger" : "btn-default";
            };
            
        }])


    /*******************************************************************
     * Controller that handles displaying message tags in a dialog
     *******************************************************************/
    .controller('MessageTagsDialogCtrl', ['$scope', '$timeout', 'growl', 'MessageService', 'AuthService',
        function ($scope, $timeout, growl, MessageService, AuthService) {
            'use strict';

            $scope.isLoggedIn = AuthService.loggedIn;
            $scope.search = '';
            $scope.data = {
                tags : []
            };

            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error accessing tags", { ttl: 5000 });
            };


            // Load the message tags for the current user
            $scope.loadTags = function() {
                delete $scope.data.editTag;
                delete $scope.data.editMode;
                MessageService.tags()
                    .success(function (tags) {
                        $scope.data.tags.length = 0;
                        angular.forEach(tags, function (tag) {
                            $scope.data.tags.push(tag);
                        });
                    })
                    .error($scope.displayError);
            };
            $scope.loadTags();

            // Set focus to the tag filter input field
            $timeout(function () { $('#tagIdFilter').focus() }, 200);

            // Adds a new tag
            $scope.addTag = function () {
                $scope.data.editMode = 'add';
                $scope.data.editTag = { tagId: '', name: '', type: 'PRIVATE', expiryDate: undefined };
            };


            // Edits an existing tag
            $scope.editTag = function (tag) {
                $scope.data.editMode = 'edit';
                $scope.data.editTag = angular.copy(tag);
            };


            // Edits an existing tag
            $scope.copyTag = function (tag) {
                $scope.data.editMode = 'add';
                $scope.data.editTag = angular.copy(tag);
            };


            // Deletes the given tag
            $scope.deleteTag = function (tag) {
                MessageService
                    .deleteMessageTag(tag)
                    .success($scope.loadTags)
                    .error($scope.displayError);
            };


            // Clears messages from the given tag
            $scope.clearTag = function (tag) {
                MessageService
                    .clearMessageTag(tag)
                    .success($scope.loadTags)
                    .error($scope.displayError);
            };


            // Saves the tag being edited
            $scope.saveTag = function () {
                if ($scope.data.editTag && $scope.data.editMode == 'add') {
                    MessageService
                        .createMessageTag($scope.data.editTag)
                        .success($scope.loadTags)
                        .error($scope.displayError);
                } else if ($scope.data.editTag && $scope.data.editMode == 'edit') {
                    MessageService
                        .updateMessageTag($scope.data.editTag)
                        .success($scope.loadTags)
                        .error($scope.displayError);
                }
            };


            // Navigate to the given link
            $scope.navigateTag = function (tag) {
                MessageService.saveLastMessageTagSelection(tag);
                $scope.$close(tag);
            };

        }])



    /*******************************************************************
     * Controller that handles the message Print dialog
     *******************************************************************/
    .controller('MessagePrintDialogCtrl', ['$scope', '$document', '$window', 'total',
        function ($scope, $document, $window, total) {
            'use strict';

            $scope.totalMessageNo = total;

            $scope.data = {
                pageSize : 'A4',
                pageOrientation: 'portrait'
            };

            if ($window.localStorage.printSettings) {
                try {
                    angular.copy(angular.fromJson($window.localStorage.printSettings), $scope.data);
                } catch (error) {
                }
            }

            // Register and unregister event-handler to listen for return key
            var eventTypes = "keydown keypress";
            function eventHandler(event) {
                if (event.which === 13) {
                    $scope.print();
                }
            }
            $document.bind(eventTypes, eventHandler);
            $scope.$on('$destroy', function () {
                $document.unbind(eventTypes, eventHandler);
            });

            // Close the print dialog and return the print settings to the callee
            $scope.print = function () {
                $window.localStorage.printSettings = angular.toJson($scope.data);
                $scope.$close($scope.data);
            };

        }])





    /*******************************************************************
     * Controller that handles sorting of messages withing an area
     *******************************************************************/
    .controller('SortAreaDialogCtrl', ['$scope', '$rootScope', '$http', '$timeout', 'MessageService',
        function ($scope, $rootScope, $http, $timeout, MessageService) {
            'use strict';

            $scope.data = {
                area: undefined,
                status: 'PUBLISHED'
            };
            $scope.messageList = [];

            /** Searches areas associated with the current domain, which have a geometry */
            $scope.searchAreas = function (name) {
                return $http.get('/rest/areas/search?name=' + encodeURIComponent(name)
                    + '&domain=' + ($rootScope.domain !== undefined)
                    + '&lang=' + $rootScope.language
                    + '&limit=10&messageSorting=true');
            };

            // Auto-completion handling for area selection
            $scope.areas = [];
            $scope.refreshAreas = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $scope
                    .searchAreas(name)
                    .then(function(response) {
                        $scope.areas = response.data;
                    });
            };

            /** Recursively formats the names of the parent lineage for areas */
            $scope.formatParents = function(child) {
                var txt = undefined;
                if (child) {
                    txt = (child.descs && child.descs.length > 0) ? child.descs[0].name : 'N/A';
                    if (child.parent) {
                        txt = $scope.formatParents(child.parent) + " - " + txt;
                    }
                }
                return txt;
            };


            /** Refreshes the list of messages matching the message filter */
            $scope.refreshMessageList = function () {
                $scope.messageList.length = 0;
                if ($scope.data.area && $scope.data.status) {
                    var params = 'area=' + $scope.data.area.id
                        + '&status=' + $scope.data.status
                        + '&sortBy=AREA&sortOrder=ASC';
                    MessageService.search(params, 0, 1000)
                        .success(function (result) {
                            for (var x = 0; x < result.data.length; x++) {
                                $scope.messageList.push(result.data[x]);
                            }
                            $scope.totalMessageNo = result.total;
                        });
                }
            };
            $scope.$watch("data", $scope.refreshMessageList, true);


            /** Updates the sort order **/
            $scope.updateMessageSortOrder = function (evt) {
                if (evt.newIndex == evt.oldIndex) {
                    return;
                }

                var index = evt.newIndex;
                var id = $scope.messageList[index].id;
                var afterId = index > 0 ? $scope.messageList[index - 1].id : null;
                var beforeId = index < $scope.messageList.length - 1 ? $scope.messageList[index + 1].id : null;
                MessageService.changeAreaSortOrder(id, afterId, beforeId)
                    .success($scope.refreshMessageList);
            };

            
            /** DnD configuration **/
            $scope.messagesSortableCfg = {
                group: 'message',
                handle: '.move-btn',
                onEnd: $scope.updateMessageSortOrder
            };


            // Initially, give focus to the area field
            $timeout(function () {
                $('#area').controller('uiSelect').activate(false, true);
            }, 100);

        }]);
