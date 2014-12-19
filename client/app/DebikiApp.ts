/* An AngularJS module for the whole page.
 * Copyright (C) 2012 - 2013 Kaj Magnus Lindberg (born 1979)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/// <reference path="ReactStore.ts" />
/// <reference path="../typedefs/angularjs/angular.d.ts" />

//------------------------------------------------------------------------------
   module debiki2 {
//------------------------------------------------------------------------------

var d = { i: debiki.internal, u: debiki.v0.util };


var dependencies = [
    'DebikiDashbarModule',
    'DebikiEditorModule',
    'ui.scrollfix'];

var pageRole = debiki2.ReactStore.getPageRole();
if (pageRole === 'Forum') {
  dependencies.push('DebikiForumModule');
}


export var DebikiApp: ng.IModule = angular.module('DebikiApp', dependencies);


//------------------------------------------------------------------------------
   }
//------------------------------------------------------------------------------
// vim: fdm=marker et ts=2 sw=2 tw=0 fo=tcqwn list
