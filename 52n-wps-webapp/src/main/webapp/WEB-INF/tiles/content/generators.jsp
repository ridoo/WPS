<%--

    Copyright (C) 2012-2015 52�North Initiative for Geospatial Open Source
    Software GmbH

    This program is free software; you can redistribute it and/or modify it
    under the terms of the GNU General Public License version 2 as published
    by the Free Software Foundation.

    If the program is linked with libraries which are licensed under one of
    the following licenses, the combination of the program with the linked
    library is not considered a "derivative work" of the program:

        - Apache License, version 2.0
        - Apache Software License, version 1.0
        - GNU Lesser General Public License, version 3
        - Mozilla Public License, versions 1.0, 1.1 and 2.0
        - Common Development and Distribution License (CDDL), version 1.0

    Therefore the distribution of the program linked with libraries licensed
    under the aforementioned licenses, is permitted by the copyright holders
    if the distribution is compliant with both the GNU General Public
    License version 2 and the aforementioned licenses.

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
    Public License for more details.

--%>
<%@ taglib prefix="module" tagdir="/WEB-INF/tags/"%>

<module:standardModule configurations="${configurations}" baseUrl="generators" />

<!-- Add format -->
<div class="modal fade" id="addFormat" tabindex="-1" role="dialog" aria-labelledby="myModalLabel" aria-hidden="true">
	<div class="modal-dialog">
		<div class="modal-content">
			<div class="modal-header">
				<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
				<h4 class="modal-title">Add a format</h4>
			</div>
			<div class="modal-body">
				<form id="addFormat" method="POST"
					action="generators/formats/add_format">
					<div class="form-group">
						<table>
							<tbody>
								<tr>
									<td><label for="mimeType">Mime type</label></td>
									<td><input type="text" name="mimeType" id="mimeType"><br /></td>
								</tr>
								<tr>
					                <td><label for="schema">Schema</label></td>
								    <td><input type="text" name="schema" id="schema"><br /></td>
								</tr>
								    <tr><td><label for="encoding">Encoding</label></td>
								    <td><input type="text" name="encoding" id="encoding"><br /></td>
								</tr>					
							</tbody>
					</table>						
						<input id="hiddenModuleName" type="hidden" />
						<p class="help-block">Please specify the mime type, schema and encoding of the format.</p>
					</div>
					<div class="form-group">
						<button type="submit" class="btn btn-primary">Add</button>
					</div>
				</form>
			</div>
		</div>
	</div>
</div>

<script src="<c:url value="/static/js/library/jquery.form.js" />"></script>
<script type="text/javascript">
	$('form#addFormat').submit(function(event) {
		event.preventDefault();
		$('#result').html('');
		var form = $(this);
		var formData = new FormData();
		formData.append("mimeType", $('#mimeType').fieldValue()[0]);
		formData.append("schema", $('#schema').fieldValue()[0]);
		formData.append("encoding", $('#encoding').fieldValue()[0]);
		formData.append("moduleClassName", $('input#hiddenModuleName').val());
		ajaxAddFormat(formData, form);
	});

	function ajaxAddFormat(formData, form) {
		// reset and clear errors and alerts
		$('#fieldError').remove();
		$('#alert').remove();
		$(".form-group").each(function() {
			$(this).removeClass("has-error");
		});
		
		$.ajax({
			url : 'generators/formats/add_format',
			data : formData,
			dataType : 'text',
			processData : false,
			contentType : false,
			headers: { 'X-CSRF-TOKEN': $('[name="csrf_token"]').attr('content') },
			type : 'POST',
			success : function(xhr) {
				// success alert
				var alertDiv = $("<div id='alert' data-dismiss class='alert alert-success'>Upload successful</div>");
				var closeBtn = $("<button>").addClass("close").attr("data-dismiss", "alert");
				closeBtn.appendTo(alertDiv).text("x");
				alertDiv.insertBefore(form);
			},
			error : function(xhr) {
				// error alert
				var alertDiv = $("<div id='alert' data-dismiss class='alert alert-danger'>Upload error</div>");
				var closeBtn = $("<button>").addClass("close").attr("data-dismiss", "alert");
				closeBtn.appendTo(alertDiv).text("x");
				alertDiv.insertBefore(form);

				var json = JSON.parse(xhr.responseText);
				var errors = json.errorMessageList;
				for ( var i = 0; i < errors.length; i++) {
					var item = errors[i];

					//display the error after the field
					var field = $('#' + item.field);
					field.parents(".form-group").addClass("has-error");
					$("<div id='fieldError' class='text-danger'>" + item.defaultMessage + "</div>").insertAfter(field);
				}
			}

		});
	}
</script>